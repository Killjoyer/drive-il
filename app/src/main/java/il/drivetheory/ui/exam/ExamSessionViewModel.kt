package il.drivetheory.ui.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.drivetheory.core.model.ExamItem
import il.drivetheory.core.model.ExamRules
import il.drivetheory.core.model.LicenseClass
import il.drivetheory.core.model.Question
import il.drivetheory.data.content.ContentRepository
import il.drivetheory.data.progress.CompletedExam
import il.drivetheory.data.progress.ProgressRepository
import il.drivetheory.data.settings.SettingsRepository
import il.drivetheory.domain.ExamGenerator
import il.drivetheory.domain.shuffledOptionOrder
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ExamSessionUiState(
    val loading: Boolean = true,
    val questions: List<Question> = emptyList(),
    val optionOrders: List<List<Int>> = emptyList(),
    /** original answer index per question, null = unanswered */
    val selected: List<Int?> = emptyList(),
    val currentIndex: Int = 0,
    val remainingMillis: Long = ExamRules.DURATION.inWholeMilliseconds,
    val notEnoughQuestions: Boolean = false,
    val saving: Boolean = false,
    /** Set once the exam is saved; the UI then navigates to the result. */
    val finishedExamId: Long? = null,
) {
    val current: Question? get() = questions.getOrNull(currentIndex)
    val answeredCount: Int get() = selected.count { it != null }
    val unansweredCount: Int get() = questions.size - answeredCount
    val isLast: Boolean get() = currentIndex == questions.lastIndex
}

/**
 * Runs one exam. Its state lives in [SavedStateHandle] so an exam survives configuration changes
 * and process death; the clock keeps running while the app is in the background, as in the real test.
 */
@HiltViewModel
class ExamSessionViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val settings: SettingsRepository,
    private val generator: ExamGenerator,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamSessionUiState())
    val uiState: StateFlow<ExamSessionUiState> = _uiState.asStateFlow()

    private var finishing = false

    init {
        viewModelScope.launch {
            if (savedState.get<IntArray>(KEY_IDS) == null) createExam() else restoreExam()
            val state = _uiState.value
            if (!state.notEnoughQuestions) runTimer()
        }
    }

    private suspend fun createExam() {
        val pool = content.questionPool().first()
        if (pool.size < ExamRules.QUESTION_COUNT) {
            _uiState.update { it.copy(loading = false, notEnoughQuestions = true) }
            return
        }
        val questions = generator.generate(pool)
        val orders = questions.map { shuffledOptionOrder(it.answers.size) }
        val now = clock.millis()
        savedState[KEY_IDS] = questions.map { it.id }.toIntArray()
        savedState[KEY_ORDERS] = orders.flatten().toIntArray()
        savedState[KEY_SELECTED] = IntArray(questions.size) { NONE }
        savedState[KEY_STARTED] = now
        savedState[KEY_LANGUAGE] = settings.currentLanguage()
        savedState[KEY_LICENSE] = settings.currentLicenseClass().code
        savedState[KEY_INDEX] = 0
        publish(questions, orders, List(questions.size) { null }, 0)
    }

    private suspend fun restoreExam() {
        val ids = savedState.get<IntArray>(KEY_IDS)!!
        val byId = content.allQuestions(savedState[KEY_LANGUAGE] ?: settings.currentLanguage()).associateBy { it.id }
        val questions = ids.map { byId.getValue(it) }
        val flatOrders = savedState.get<IntArray>(KEY_ORDERS)!!
        var offset = 0
        val orders = questions.map { q -> flatOrders.slice(offset until offset + q.answers.size).also { offset += q.answers.size } }
        val selected = savedState.get<IntArray>(KEY_SELECTED)!!.map { if (it == NONE) null else it }
        publish(questions, orders, selected, savedState[KEY_INDEX] ?: 0)
    }

    private fun publish(questions: List<Question>, orders: List<List<Int>>, selected: List<Int?>, index: Int) {
        _uiState.update {
            it.copy(
                loading = false,
                questions = questions,
                optionOrders = orders,
                selected = selected,
                currentIndex = index,
                remainingMillis = remaining(),
            )
        }
    }

    private fun deadline(): Long = (savedState.get<Long>(KEY_STARTED) ?: clock.millis()) + ExamRules.DURATION.inWholeMilliseconds
    private fun remaining(): Long = (deadline() - clock.millis()).coerceAtLeast(0)

    private suspend fun runTimer() {
        while (viewModelScope.isActive && !finishing) {
            val left = remaining()
            _uiState.update { it.copy(remainingMillis = left) }
            if (left <= 0) {
                finish(timedOut = true)
                return
            }
            delay((left % 1000).takeIf { it > 0 } ?: 1000)
        }
    }

    fun select(originalIndex: Int) {
        val state = _uiState.value
        if (state.loading || finishing) return
        val selected = state.selected.toMutableList().also { it[state.currentIndex] = originalIndex }
        savedState[KEY_SELECTED] = selected.map { it ?: NONE }.toIntArray()
        _uiState.update { it.copy(selected = selected) }
    }

    fun goTo(index: Int) {
        val state = _uiState.value
        if (index !in state.questions.indices) return
        savedState[KEY_INDEX] = index
        _uiState.update { it.copy(currentIndex = index) }
    }

    fun next() = goTo(_uiState.value.currentIndex + 1)
    fun previous() = goTo(_uiState.value.currentIndex - 1)

    fun finish() = viewModelScope.launch { finish(timedOut = false) }

    private suspend fun finish(timedOut: Boolean) {
        if (finishing) return
        finishing = true
        val state = _uiState.value
        _uiState.update { it.copy(saving = true) }
        val started = savedState.get<Long>(KEY_STARTED) ?: clock.millis()
        val id = progress.saveExam(
            CompletedExam(
                licenseClass = savedState.get<String>(KEY_LICENSE)?.let(LicenseClass::fromCode) ?: LicenseClass.DEFAULT,
                startedAt = started,
                finishedAt = minOf(clock.millis(), deadline()),
                timedOut = timedOut,
                items = state.questions.mapIndexed { i, q -> ExamItem(q, state.optionOrders[i], state.selected[i]) },
            ),
        )
        savedState.remove<IntArray>(KEY_IDS)
        _uiState.update { it.copy(finishedExamId = id) }
    }

    private companion object {
        const val NONE = -1
        const val KEY_IDS = "exam_ids"
        const val KEY_ORDERS = "exam_orders"
        const val KEY_SELECTED = "exam_selected"
        const val KEY_STARTED = "exam_started"
        const val KEY_LANGUAGE = "exam_language"
        const val KEY_LICENSE = "exam_license"
        const val KEY_INDEX = "exam_index"
    }
}
