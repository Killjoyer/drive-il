package il.drivetheory.ui.exam

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import il.drivetheory.core.model.ExamDetails
import il.drivetheory.core.model.ExamItem
import il.drivetheory.core.model.Topic
import il.drivetheory.data.content.ContentRepository
import il.drivetheory.data.progress.ProgressRepository
import il.drivetheory.data.settings.SettingsRepository
import il.drivetheory.ui.navigation.ExamResultRoute
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ResultFilter { ALL, WRONG, CORRECT }

data class TopicScore(val topic: Topic, val correct: Int, val total: Int)

sealed interface ExamResultUiState {
    data object Loading : ExamResultUiState
    data object NotFound : ExamResultUiState
    data class Ready(
        val details: ExamDetails,
        val topicScores: List<TopicScore>,
        val filter: ResultFilter = ResultFilter.ALL,
    ) : ExamResultUiState {
        val visibleItems: List<IndexedValue<ExamItem>>
            get() = details.items.withIndex().filter {
                when (filter) {
                    ResultFilter.ALL -> true
                    ResultFilter.WRONG -> !it.value.isCorrect
                    ResultFilter.CORRECT -> it.value.isCorrect
                }
            }
    }
}

@HiltViewModel
class ExamResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    content: ContentRepository,
    progress: ProgressRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val examId = savedStateHandle.toRoute<ExamResultRoute>().examId
    private val _uiState = MutableStateFlow<ExamResultUiState>(ExamResultUiState.Loading)
    val uiState: StateFlow<ExamResultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val questions = content.allQuestions(settings.currentLanguage()).associateBy { it.id }
            val details = progress.examDetails(examId, questions)
            _uiState.value = if (details == null) ExamResultUiState.NotFound else ExamResultUiState.Ready(
                details = details,
                topicScores = details.items.groupBy { it.question.topic }.toSortedMap().map { (topic, items) ->
                    TopicScore(topic, items.count { it.isCorrect }, items.size)
                },
                filter = if (details.summary.mistakes > 0) ResultFilter.WRONG else ResultFilter.ALL,
            )
        }
    }

    fun setFilter(filter: ResultFilter) {
        _uiState.update { if (it is ExamResultUiState.Ready) it.copy(filter = filter) else it }
    }
}
