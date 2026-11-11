package il.drivetheory.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.drivetheory.R
import il.drivetheory.core.model.LicenseClass
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var confirmImport by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                SettingsMessage.Exported -> resources.getString(R.string.backup_exported)
                is SettingsMessage.Imported -> resources.getString(
                    R.string.backup_imported, message.summary.questionCount, message.summary.examCount,
                )
                SettingsMessage.InvalidFile -> resources.getString(R.string.backup_invalid)
                is SettingsMessage.TooLarge -> resources.getString(R.string.backup_too_large, message.limitMb)
                SettingsMessage.Failed -> resources.getString(R.string.backup_failed)
                SettingsMessage.Reset -> resources.getString(R.string.progress_reset_done)
            }
            snackbar.showSnackbar(text)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.padding(horizontal = 16.dp))
            if (state.availableLanguages.size > 1) {
                SectionTitle(stringResource(R.string.settings_language))
                Text(
                    stringResource(R.string.settings_language_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Column(Modifier.selectableGroup()) {
                    state.availableLanguages.forEach { lang ->
                        val locale = Locale.forLanguageTag(lang)
                        ListItem(
                            headlineContent = { Text(locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }) },
                            leadingContent = { RadioButton(selected = state.language == lang, onClick = null) },
                            modifier = Modifier.selectable(
                                selected = state.language == lang,
                                role = Role.RadioButton,
                                onClick = { viewModel.setLanguage(lang) },
                            ),
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            SectionTitle(stringResource(R.string.settings_license))
            Text(
                stringResource(R.string.settings_license_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.selectableGroup()) {
                LicenseClass.entries.forEach { license ->
                    ListItem(
                        headlineContent = { Text(licenseTitle(license)) },
                        leadingContent = { RadioButton(selected = state.licenseClass == license, onClick = null) },
                        modifier = Modifier.selectable(
                            selected = state.licenseClass == license,
                            role = Role.RadioButton,
                            onClick = { viewModel.setLicenseClass(license) },
                        ),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_progress))
            Text(
                stringResource(R.string.settings_progress_summary, state.answeredQuestions, state.examCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_export)) },
                supportingContent = { Text(stringResource(R.string.backup_export_hint)) },
                leadingContent = { Icon(Icons.Filled.Share, null) },
                modifier = Modifier.clickable(enabled = !state.busy, role = Role.Button) {
                    exportLauncher.launch("drive-theory-progress-${LocalDate.now()}.json")
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_import)) },
                supportingContent = { Text(stringResource(R.string.backup_import_hint)) },
                leadingContent = { Icon(Icons.Filled.Refresh, null) },
                modifier = Modifier.clickable(enabled = !state.busy, role = Role.Button) {
                    confirmImport = true
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.progress_reset)) },
                leadingContent = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(enabled = !state.busy, role = Role.Button) {
                    confirmReset = true
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_title)) },
                supportingContent = { Text(stringResource(R.string.about_hint)) },
                leadingContent = { Icon(Icons.Filled.Info, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpenAbout),
            )
        }
    }

    if (confirmImport) {
        ConfirmDialog(
            title = stringResource(R.string.backup_import),
            text = stringResource(R.string.backup_import_confirm),
            confirm = stringResource(R.string.backup_import_choose),
            onConfirm = {
                confirmImport = false
                importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            },
            onDismiss = { confirmImport = false },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.progress_reset),
            text = stringResource(R.string.progress_reset_confirm),
            confirm = stringResource(R.string.progress_reset_action),
            onConfirm = { confirmReset = false; viewModel.resetProgress() },
            onDismiss = { confirmReset = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun licenseTitle(license: LicenseClass): String = stringResource(
    when (license) {
        LicenseClass.B -> R.string.license_b
        LicenseClass.A -> R.string.license_a
        LicenseClass.TRACTOR -> R.string.license_1
        LicenseClass.C1 -> R.string.license_c1
        LicenseClass.C -> R.string.license_c
        LicenseClass.D -> R.string.license_d
    },
)
