package il.drivetheory.ui.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.drivetheory.R
import il.drivetheory.core.model.ExamRules
import il.drivetheory.core.model.ExamSummary
import il.drivetheory.ui.components.LoadingBox
import il.drivetheory.ui.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
fun formatDate(millis: Long): String = DATE_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamHomeScreen(
    onStartExam: () -> Unit,
    onOpenResult: (Long) -> Unit,
    viewModel: ExamHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_exam)) }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        when (val s = state) {
            ExamHomeUiState.Loading -> LoadingBox(Modifier.padding(padding))
            is ExamHomeUiState.Ready -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { RulesCard(s, onStartExam) }
                if (s.history.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.exam_history),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    item {
                        Text(
                            stringResource(
                                R.string.exam_history_summary,
                                s.history.size, s.passedCount, s.recentPassRate ?: 0,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    items(s.history, key = { it.id }) { exam -> HistoryItem(exam) { onOpenResult(exam.id) } }
                }
            }
        }
    }
}

@Composable
private fun RulesCard(state: ExamHomeUiState.Ready, onStart: () -> Unit) {
    ElevatedCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.exam_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.exam_license_pool, state.licenseClass.code, state.poolSize),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(
                stringResource(R.string.exam_rule_questions, ExamRules.QUESTION_COUNT),
                stringResource(R.string.exam_rule_time, ExamRules.DURATION.inWholeMinutes),
                stringResource(R.string.exam_rule_pass, ExamRules.PASS_MIN_CORRECT, ExamRules.MAX_MISTAKES),
                stringResource(R.string.exam_rule_navigation),
                stringResource(R.string.exam_rule_unanswered),
                stringResource(R.string.exam_rule_pause),
                stringResource(R.string.exam_rule_topics),
            ).forEach { rule ->
                Row {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(rule, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onStart,
                enabled = state.poolSize >= ExamRules.QUESTION_COUNT,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.exam_start))
            }
        }
    }
}

@Composable
private fun HistoryItem(exam: ExamSummary, onClick: () -> Unit) {
    val feedback = AppTheme.feedback
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDate(exam.finishedAt), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.exam_history_item, exam.correctCount, exam.questionCount, exam.licenseClass.code),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(if (exam.passed) R.string.exam_passed_short else R.string.exam_failed_short),
                color = if (exam.passed) feedback.correct else feedback.wrong,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
