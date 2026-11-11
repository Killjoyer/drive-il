package il.drivetheory.ui.exam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.drivetheory.R
import il.drivetheory.core.model.ExamItem
import il.drivetheory.core.model.ExamRules
import il.drivetheory.core.model.ExamSummary
import il.drivetheory.ui.components.AnswerOption
import il.drivetheory.ui.components.EmptyState
import il.drivetheory.ui.components.LoadingBox
import il.drivetheory.ui.components.OptionState
import il.drivetheory.ui.components.QuestionHeader
import il.drivetheory.ui.components.formatDuration
import il.drivetheory.ui.components.topicTitle
import il.drivetheory.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamResultScreen(
    onBack: () -> Unit,
    onNewExam: () -> Unit,
    viewModel: ExamResultViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exam_result_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            ExamResultUiState.Loading -> LoadingBox(Modifier.padding(padding))
            ExamResultUiState.NotFound -> EmptyState(
                stringResource(R.string.exam_result_missing_title), stringResource(R.string.exam_result_missing),
                Modifier.padding(padding),
            )
            is ExamResultUiState.Ready -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SummaryCard(s.details.summary, s.topicScores, onNewExam) }
                item { FilterRow(s, viewModel::setFilter) }
                if (s.visibleItems.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.exam_result_no_items),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(s.visibleItems, key = { it.index }) { (index, item) -> ResultItem(index, item) }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: ExamSummary, topics: List<TopicScore>, onNewExam: () -> Unit) {
    val feedback = AppTheme.feedback
    ElevatedCard {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                color = if (summary.passed) feedback.correctContainer else feedback.wrongContainer,
                contentColor = if (summary.passed) feedback.onCorrectContainer else feedback.onWrongContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(if (summary.passed) R.string.exam_passed else R.string.exam_failed),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.exam_score, summary.correctCount, summary.questionCount),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Text(
                stringResource(R.string.exam_mistakes_line, summary.mistakes, ExamRules.MAX_MISTAKES),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.exam_time_used, formatDuration(summary.durationMillis), ExamRules.DURATION.inWholeMinutes,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (summary.timedOut) {
                Text(
                    stringResource(R.string.exam_timed_out),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                stringResource(R.string.exam_license_line, summary.licenseClass.code, formatDate(summary.finishedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(stringResource(R.string.exam_by_topic), style = MaterialTheme.typography.titleSmall)
            topics.forEach { score ->
                Row(Modifier.fillMaxWidth()) {
                    Text(topicTitle(score.topic), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${score.correct} / ${score.total}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (score.correct == score.total) feedback.correct else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Button(onClick = onNewExam, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.exam_new))
            }
        }
    }
}

@Composable
private fun FilterRow(state: ExamResultUiState.Ready, onFilter: (ResultFilter) -> Unit) {
    val items = state.details.items
    val wrong = items.count { !it.isCorrect }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = state.filter == ResultFilter.WRONG,
                onClick = { onFilter(ResultFilter.WRONG) },
                label = { Text(stringResource(R.string.filter_wrong, wrong)) },
            )
        }
        item {
            FilterChip(
                selected = state.filter == ResultFilter.CORRECT,
                onClick = { onFilter(ResultFilter.CORRECT) },
                label = { Text(stringResource(R.string.filter_correct, items.size - wrong)) },
            )
        }
        item {
            FilterChip(
                selected = state.filter == ResultFilter.ALL,
                onClick = { onFilter(ResultFilter.ALL) },
                label = { Text(stringResource(R.string.filter_all, items.size)) },
            )
        }
    }
}

@Composable
private fun ResultItem(index: Int, item: ExamItem) {
    val feedback = AppTheme.feedback
    val question = item.question
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (item.isCorrect) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (item.isCorrect) feedback.correct else feedback.wrong,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        when {
                            item.isCorrect -> R.string.result_item_correct
                            item.isAnswered -> R.string.result_item_wrong
                            else -> R.string.result_item_unanswered
                        },
                        index + 1,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (item.isCorrect) feedback.correct else feedback.wrong,
                )
            }
            QuestionHeader(question)
            item.optionOrder.forEachIndexed { position, original ->
                AnswerOption(
                    letter = position,
                    text = question.answers[original],
                    state = when (original) {
                        question.correctIndex -> OptionState.CORRECT
                        item.selectedIndex -> OptionState.WRONG
                        else -> OptionState.NEUTRAL
                    },
                    enabled = false,
                    onClick = {},
                )
            }
            Explanation(item)
        }
    }
}

/**
 * The official pool contains no written explanations, so the breakdown states what was chosen,
 * why it is scored the way it is, and which answer the Ministry's key marks as correct.
 */
@Composable
private fun Explanation(item: ExamItem) {
    val question = item.question
    val correctText = question.answers[question.correctIndex]
    val text = when {
        item.isCorrect -> stringResource(R.string.explanation_correct, correctText)
        item.isAnswered -> stringResource(
            R.string.explanation_wrong, question.answers[item.selectedIndex!!], correctText,
        )
        else -> stringResource(R.string.explanation_unanswered, correctText)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.explanation_title), style = MaterialTheme.typography.labelLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
