package il.drivetheory.ui.exam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.drivetheory.core.model.ExamSummary
import il.drivetheory.core.model.LicenseClass
import il.drivetheory.data.content.ContentRepository
import il.drivetheory.data.progress.ProgressRepository
import il.drivetheory.data.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface ExamHomeUiState {
    data object Loading : ExamHomeUiState
    data class Ready(
        val licenseClass: LicenseClass,
        val poolSize: Int,
        val history: List<ExamSummary>,
    ) : ExamHomeUiState {
        val passedCount: Int get() = history.count { it.passed }
        /** Share of the last (up to) 5 exams that were passed — a simple readiness signal. */
        val recentPassRate: Int? get() = history.take(5).takeIf { it.isNotEmpty() }
            ?.let { recent -> recent.count { it.passed } * 100 / recent.size }
    }
}

@HiltViewModel
class ExamHomeViewModel @Inject constructor(
    content: ContentRepository,
    progress: ProgressRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<ExamHomeUiState> = combine(
        content.questionPool(),
        progress.examHistory,
        settings.licenseClass,
    ) { pool, history, license ->
        ExamHomeUiState.Ready(license, pool.size, history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamHomeUiState.Loading)
}
