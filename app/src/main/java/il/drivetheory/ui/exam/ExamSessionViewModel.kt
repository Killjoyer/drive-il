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
import kotlin.time.Duration
import kotlin.time.TimeSource
import javax.inject.Inject
import kotlinx.coroutines.Job
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
    /** The app was killed during the exam; it is not resumed. */
    val interrupted: Boolean = false,
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
 * Runs one exam. The clock only runs while the exam screen is in the foreground (see [resumeClock] /
 * [pauseClock]), so locking the phone or switching apps pauses it.
 *
 * An exam is deliberately not restored after the process dies: [SavedStateHandle] only remembers
 * that an exam was running, and a recreated view model reports [ExamSessionUiState.interrupted].
 * Nothing of an unfinished exam is stored — per-question statistics only change when it is finished.
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
    private var license: LicenseClass = LicenseClass.DEFAULT

    /** Exam time used before the current foreground stretch. */
    private var elapsedBefore = Duration.ZERO
    /** Start of the current foreground stretch, null while paused. */
    private var runningSince: TimeSource.Monotonic.ValueTimeMark? = null
    private var ticker: Job? = null
    private var wantsClock = false

    init {
        if (savedState.get<Boolean>(KEY_RUNNING) == true) {
            _uiState.update { it.copy(loading = false, interrupted = true) }
        } else {
            savedState[KEY_RUNNING] = true
            viewModelScope.launch { createExam() }
        }
    }

    private suspend fun createExam() {
        val pool = content.questionPool().first()
        if (pool.size < ExamRules.QUESTION_COUNT) {
            _uiState.update { it.copy(loading = false, notEnoughQuestions = true) }
            return
        }
        license = settings.currentLicenseClass()
        val questions = generator.generate(pool)
        _uiState.update {
            it.copy(
                loading = false,
                questions = questions,
                optionOrders = questions.map { q -> shuffledOptionOrder(q.answers.size) },
                selected = List(questions.size) { null },
            )
        }
        if (wantsClock) resumeClock()
    }

    private fun elapsed(): Duration = elapsedBefore + (runningSince?.elapsedNow() ?: Duration.ZERO)
    private fun remaining(): Long = (ExamRules.DURATION - elapsed()).inWholeMilliseconds.coerceAtLeast(0)

    /** Called when the exam screen comes to the foreground. */
    fun resumeClock() {
        wantsClock = true
        val state = _uiState.value
        if (state.questions.isEmpty() || finishing || runningSince != null) return
        runningSince = TimeSource.Monotonic.markNow()
        ticker = viewModelScope.launch {
            while (isActive) {
                val left = remaining()
                _uiState.update { it.copy(remainingMillis = left) }
                if (left <= 0) {
                    // Saved outside the ticker so pausing the clock cannot cancel the save.
                    viewModelScope.launch { finish(timedOut = true) }
                    return@launch
                }
                delay((left % 1000).takeIf { it > 0 } ?: 1000)
            }
        }
    }

    /** Called when the exam screen leaves the foreground (screen locked, app switched, ...). */
    fun pauseClock() {
        wantsClock = false
        if (finishing) return
        runningSince?.let { elapsedBefore += it.elapsedNow() }
        runningSince = null
        ticker?.cancel()
        ticker = null
        _uiState.update { it.copy(remainingMillis = remaining()) }
    }

    fun select(originalIndex: Int) {
        val state = _uiState.value
        if (state.questions.isEmpty() || finishing) return
        val selected = state.selected.toMutableList().also { it[state.currentIndex] = originalIndex }
        _uiState.update { it.copy(selected = selected) }
    }

    fun goTo(index: Int) {
        if (index !in _uiState.value.questions.indices) return
        _uiState.update { it.copy(currentIndex = index) }
    }

    fun next() = goTo(_uiState.value.currentIndex + 1)
    fun previous() = goTo(_uiState.value.currentIndex - 1)

    fun finish() = viewModelScope.launch { finish(timedOut = false) }

    private suspend fun finish(timedOut: Boolean) {
        if (finishing) return
        finishing = true
        val used = elapsed().coerceAtMost(ExamRules.DURATION).inWholeMilliseconds
        runningSince = null
        ticker?.cancel()
        val state = _uiState.value
        _uiState.update { it.copy(saving = true) }
        val now = clock.millis()
        val id = progress.saveExam(
            CompletedExam(
                licenseClass = license,
                // Only time spent with the exam on screen counts.
                startedAt = now - used,
                finishedAt = now,
                timedOut = timedOut,
                items = state.questions.mapIndexed { i, q -> ExamItem(q, state.optionOrders[i], state.selected[i]) },
            ),
        )
        _uiState.update { it.copy(finishedExamId = id) }
    }

    private companion object {
        const val KEY_RUNNING = "exam_running"
    }
}
