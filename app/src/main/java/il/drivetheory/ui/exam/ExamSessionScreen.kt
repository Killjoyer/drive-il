package il.drivetheory.ui.exam

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.drivetheory.R
import il.drivetheory.core.model.ExamRules
import il.drivetheory.ui.components.AnswerOption
import il.drivetheory.ui.components.EmptyState
import il.drivetheory.ui.components.LoadingBox
import il.drivetheory.ui.components.OptionState
import il.drivetheory.ui.components.QuestionHeader
import il.drivetheory.ui.components.formatDuration

private const val LOW_TIME_MS = 5 * 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamSessionScreen(
    onFinished: (Long) -> Unit,
    onAbandon: () -> Unit,
    viewModel: ExamSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    var confirmAbandon by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.finishedExamId) { state.finishedExamId?.let(onFinished) }
    BackHandler(enabled = !state.loading && !state.notEnoughQuestions) { confirmAbandon = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (state.questions.isNotEmpty()) {
                        Text(stringResource(R.string.exam_question_of, state.currentIndex + 1, state.questions.size))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (state.notEnoughQuestions) onAbandon() else confirmAbandon = true }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.exam_abandon))
                    }
                },
                actions = {
                    if (state.questions.isNotEmpty()) {
                        TimerChip(state.remainingMillis)
                        TextButton(onClick = { confirmFinish = true }, enabled = !state.saving) {
                            Text(stringResource(R.string.exam_finish))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.questions.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::previous,
                            enabled = state.currentIndex > 0,
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.action_previous)) }
                        if (state.isLast) {
                            Button(onClick = { confirmFinish = true }, enabled = !state.saving, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.exam_finish))
                            }
                        } else {
                            Button(onClick = viewModel::next, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.action_next))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.notEnoughQuestions -> EmptyState(
                title = stringResource(R.string.exam_not_enough_title),
                message = stringResource(R.string.exam_not_enough_message, ExamRules.QUESTION_COUNT),
                modifier = Modifier.padding(padding),
            )
            else -> ExamQuestionPage(state, viewModel::select, viewModel::goTo, Modifier.padding(padding))
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.exam_finish_confirm_title)) },
            text = {
                Text(
                    if (state.unansweredCount == 0) stringResource(R.string.exam_finish_confirm_all)
                    else pluralStringResource(R.plurals.exam_unanswered_warning, state.unansweredCount, state.unansweredCount),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmFinish = false; viewModel.finish() }) { Text(stringResource(R.string.exam_finish)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.exam_continue)) }
            },
        )
    }
    if (confirmAbandon) {
        AlertDialog(
            onDismissRequest = { confirmAbandon = false },
            title = { Text(stringResource(R.string.exam_abandon_title)) },
            text = { Text(stringResource(R.string.exam_abandon_message)) },
            confirmButton = {
                TextButton(onClick = { confirmAbandon = false; onAbandon() }) { Text(stringResource(R.string.exam_abandon)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAbandon = false }) { Text(stringResource(R.string.exam_continue)) }
            },
        )
    }
}

@Composable
private fun TimerChip(remainingMillis: Long) {
    val low = remainingMillis <= LOW_TIME_MS
    val description = stringResource(R.string.exam_time_left_description, formatDuration(remainingMillis))
    Surface(
        shape = CircleShape,
        color = if (low) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (low) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = formatDuration(remainingMillis),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ExamQuestionPage(
    state: ExamSessionUiState,
    onSelect: (Int) -> Unit,
    onGoTo: (Int) -> Unit,
    modifier: Modifier,
) {
    val question = state.current ?: return
    val scroll = rememberScrollState()
    val navState = rememberLazyListState()
    LaunchedEffect(state.currentIndex) {
        scroll.scrollTo(0)
        navState.animateScrollToItem((state.currentIndex - 3).coerceAtLeast(0))
    }
    Column(modifier.fillMaxSize()) {
        QuestionNavigator(state, onGoTo, navState)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuestionHeader(question)
            val selected = state.selected[state.currentIndex]
            state.optionOrders[state.currentIndex].forEachIndexed { position, original ->
                AnswerOption(
                    letter = position,
                    text = question.answers[original],
                    state = if (original == selected) OptionState.SELECTED else OptionState.NEUTRAL,
                    enabled = !state.saving,
                    onClick = { onSelect(original) },
                )
            }
            Text(
                text = stringResource(R.string.exam_answered_count, state.answeredCount, state.questions.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun QuestionNavigator(
    state: ExamSessionUiState,
    onGoTo: (Int) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.questions, key = { _, q -> q.id }) { index, _ ->
            val answered = state.selected[index] != null
            val current = index == state.currentIndex
            val scheme = MaterialTheme.colorScheme
            val label = stringResource(
                if (answered) R.string.exam_nav_answered else R.string.exam_nav_unanswered, index + 1,
            )
            Surface(
                onClick = { onGoTo(index) },
                shape = CircleShape,
                color = if (answered) scheme.primary else scheme.surfaceContainerHigh,
                contentColor = if (answered) scheme.onPrimary else scheme.onSurface,
                border = if (current) BorderStroke(3.dp, scheme.tertiary) else null,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = label },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
