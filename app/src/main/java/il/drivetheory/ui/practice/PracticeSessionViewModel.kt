package il.drivetheory.ui.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import il.drivetheory.core.model.Question
import il.drivetheory.core.model.QuestionStat
import il.drivetheory.data.content.ContentRepository
import il.drivetheory.data.progress.ProgressRepository
import il.drivetheory.domain.LearningProgress
import il.drivetheory.domain.PracticeSelector
import il.drivetheory.domain.shuffledOptionOrder
import il.drivetheory.ui.navigation.PracticeMode
import il.drivetheory.ui.navigation.PracticeSessionRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PracticeUiState(
    val mode: PracticeMode,
    val loading: Boolean = true,
    val question: Question? = null,
    /** displayed position -> original answer index */
    val optionOrder: List<Int> = emptyList(),
    /** original index of the chosen answer, null until answered */
    val selectedIndex: Int? = null,
    /** History of the current question before this attempt. */
    val previousStat: QuestionStat? = null,
    val progress: LearningProgress = LearningProgress(0, 0),
    val sessionCorrect: Int = 0,
    val sessionWrong: Int = 0,
    /** True when there is nothing to practise (e.g. no open mistakes left). */
    val finished: Boolean = false,
) {
    val answered: Boolean get() = selectedIndex != null
    val answeredCorrectly: Boolean get() = question != null && selectedIndex == question.correctIndex
}

@HiltViewModel
class PracticeSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    content: ContentRepository,
    private val progressRepository: ProgressRepository,
    private val selector: PracticeSelector,
) : ViewModel() {

    private val mode = PracticeMode.decode(savedStateHandle.toRoute<PracticeSessionRoute>().mode)
    private val _uiState = MutableStateFlow(PracticeUiState(mode))
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private var pool: List<Question> = emptyList()
    private var stats: Map<Int, QuestionStat> = emptyMap()
    private val recent = ArrayDeque<Int>()
    /** For the mistakes mode: the mistakes that were open when the session started. */
    private var initialMistakes: Set<Int>? = null

    init {
        viewModelScope.launch {
            combine(content.questionPool(), progressRepository.stats, ::Pair).collect { (all, newStats) ->
                pool = when (mode) {
                    PracticeMode.All, PracticeMode.Mistakes -> all
                    is PracticeMode.OfTopic -> all.filter { it.topic == mode.topic }
                }
                stats = newStats
                if (mode == PracticeMode.Mistakes && initialMistakes == null) {
                    initialMistakes = pool.filter { newStats[it.id]?.isOpenMistake == true }.mapTo(HashSet()) { it.id }
                }
                _uiState.update { it.copy(progress = currentProgress()) }
                val current = _uiState.value
                val currentGone = current.question != null && pool.none { it.id == current.question.id }
                if (current.loading || currentGone) next()
            }
        }
    }

    fun select(originalIndex: Int) {
        val state = _uiState.value
        val question = state.question ?: return
        if (state.answered) return
        val correct = originalIndex == question.correctIndex
        _uiState.update {
            it.copy(
                selectedIndex = originalIndex,
                sessionCorrect = it.sessionCorrect + if (correct) 1 else 0,
                sessionWrong = it.sessionWrong + if (correct) 0 else 1,
            )
        }
        viewModelScope.launch { progressRepository.recordAnswer(question.id, correct) }
    }

    fun next() {
        val candidates = when (mode) {
            PracticeMode.Mistakes -> pool.filter { stats[it.id]?.isOpenMistake == true }
            else -> pool
        }
        val question = selector.next(candidates, stats, recent.toList())
        question?.let {
            recent.addLast(it.id)
            while (recent.size > PracticeSelector.RECENT_WINDOW) recent.removeFirst()
        }
        _uiState.update {
            it.copy(
                loading = false,
                question = question,
                optionOrder = question?.let { q -> shuffledOptionOrder(q.answers.size) }.orEmpty(),
                selectedIndex = null,
                previousStat = question?.let { q -> stats[q.id] },
                progress = currentProgress(),
                finished = question == null,
            )
        }
    }

    private fun currentProgress(): LearningProgress = when (mode) {
        PracticeMode.Mistakes -> {
            val initial = initialMistakes.orEmpty()
            LearningProgress(initial.count { stats[it]?.isOpenMistake != true }, initial.size)
        }
        else -> LearningProgress.of(pool, stats)
    }
}
