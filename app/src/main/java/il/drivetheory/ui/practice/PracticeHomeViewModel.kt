package il.drivetheory.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.drivetheory.core.model.LicenseClass
import il.drivetheory.core.model.Topic
import il.drivetheory.data.content.ContentRepository
import il.drivetheory.data.progress.ProgressRepository
import il.drivetheory.data.settings.SettingsRepository
import il.drivetheory.domain.LearningProgress
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TopicProgress(val topic: Topic, val progress: LearningProgress)

sealed interface PracticeHomeUiState {
    data object Loading : PracticeHomeUiState
    data class Ready(
        val licenseClass: LicenseClass,
        val overall: LearningProgress,
        val topics: List<TopicProgress>,
        val openMistakes: Int,
        val answeredTotal: Int,
    ) : PracticeHomeUiState
}

@HiltViewModel
class PracticeHomeViewModel @Inject constructor(
    content: ContentRepository,
    progress: ProgressRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<PracticeHomeUiState> = combine(
        content.questionPool(),
        progress.stats,
        settings.licenseClass,
    ) { pool, stats, license ->
        val poolIds = pool.mapTo(HashSet()) { it.id }
        PracticeHomeUiState.Ready(
            licenseClass = license,
            overall = LearningProgress.of(pool, stats),
            topics = Topic.entries.mapNotNull { topic ->
                val ofTopic = pool.filter { it.topic == topic }
                if (ofTopic.isEmpty()) null else TopicProgress(topic, LearningProgress.of(ofTopic, stats))
            },
            openMistakes = stats.values.count { it.isOpenMistake && it.questionId in poolIds },
            answeredTotal = stats.values.sumOf { it.correctCount + it.wrongCount },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeHomeUiState.Loading)
}
