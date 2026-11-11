package il.drivetheory.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.drivetheory.core.model.LicenseClass
import il.drivetheory.data.backup.BackupRepository
import il.drivetheory.data.backup.ImportSummary
import il.drivetheory.data.backup.InvalidBackupException
import il.drivetheory.data.content.ContentRepository
import il.drivetheory.data.progress.ProgressRepository
import il.drivetheory.data.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val licenseClass: LicenseClass = LicenseClass.DEFAULT,
    val language: String = SettingsRepository.DEFAULT_LANGUAGE,
    /** Bundled content languages; the picker is shown only when there is a choice. */
    val availableLanguages: List<String> = emptyList(),
    val answeredQuestions: Int = 0,
    val examCount: Int = 0,
    val busy: Boolean = false,
)

sealed interface SettingsMessage {
    data object Exported : SettingsMessage
    data class Imported(val summary: ImportSummary) : SettingsMessage
    data object InvalidFile : SettingsMessage
    data object Failed : SettingsMessage
    data object Reset : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val backup: BackupRepository,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val languages = flow { emit(content.availableLanguages()) }
    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(settings.licenseClass, settings.contentLanguage, languages, ::Triple),
        progress.stats, progress.examHistory, busy,
    ) { (license, language, available), stats, exams, isBusy ->
        SettingsUiState(license, language, available, stats.size, exams.size, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setLicenseClass(value: LicenseClass) = viewModelScope.launch { settings.setLicenseClass(value) }

    fun setLanguage(value: String) = viewModelScope.launch { settings.setContentLanguage(value) }

    fun export(uri: Uri) = runBusy { backup.export(uri); SettingsMessage.Exported }

    fun import(uri: Uri) = runBusy { SettingsMessage.Imported(backup.import(uri)) }

    fun resetProgress() = runBusy { progress.resetAll(); SettingsMessage.Reset }

    private fun runBusy(block: suspend () -> SettingsMessage) = viewModelScope.launch {
        busy.value = true
        val message = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: InvalidBackupException) {
            SettingsMessage.InvalidFile
        } catch (_: Exception) {
            SettingsMessage.Failed
        } finally {
            busy.value = false
        }
        _messages.send(message)
    }
}
