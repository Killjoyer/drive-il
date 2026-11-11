package il.drivetheory.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.drivetheory.R
import il.drivetheory.ui.components.AnswerOption
import il.drivetheory.ui.components.EmptyState
import il.drivetheory.ui.components.FeedbackBanner
import il.drivetheory.ui.components.LoadingBox
import il.drivetheory.ui.components.OptionState
import il.drivetheory.ui.components.ProgressLine
import il.drivetheory.ui.components.QuestionHeader
import il.drivetheory.ui.components.topicTitle
import il.drivetheory.ui.navigation.PracticeMode
import il.drivetheory.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSessionScreen(
    onBack: () -> Unit,
    viewModel: PracticeSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(modeTitle(state.mode)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            if (state.question != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.answered) {
                            Button(onClick = viewModel::next, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.action_next_question))
                            }
                        } else {
                            OutlinedButton(onClick = viewModel::next, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.action_skip))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingBox(Modifier.padding(padding))
            state.finished -> EmptyState(
                title = stringResource(R.string.practice_done_title),
                message = stringResource(
                    if (state.mode == PracticeMode.Mistakes) R.string.practice_done_mistakes else R.string.practice_done_empty,
                ),
                modifier = Modifier.padding(padding),
                action = { Button(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
            else -> PracticeQuestion(state, viewModel::select, Modifier.padding(padding))
        }
    }
}

@Composable
private fun PracticeQuestion(state: PracticeUiState, onSelect: (Int) -> Unit, modifier: Modifier) {
    val question = state.question ?: return
    val scroll = rememberScrollState()
    LaunchedEffect(question.id) { scroll.scrollTo(0) }
    LaunchedEffect(state.answered) { if (state.answered) scroll.animateScrollTo(scroll.maxValue) }
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ProgressLine(state.progress)
            Row {
                Text(
                    text = stringResource(
                        if (state.mode == PracticeMode.Mistakes) R.string.practice_fixed_of else R.string.learned_of,
                        state.progress.learned, state.progress.total,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.session_score, state.sessionCorrect, state.sessionWrong),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuestionHeader(question)
            state.optionOrder.forEachIndexed { position, original ->
                val optionState = when {
                    !state.answered -> OptionState.NEUTRAL
                    original == question.correctIndex -> OptionState.CORRECT
                    original == state.selectedIndex -> OptionState.WRONG
                    else -> OptionState.NEUTRAL
                }
                AnswerOption(
                    letter = position,
                    text = question.answers[original],
                    state = optionState,
                    enabled = !state.answered,
                    onClick = { onSelect(original) },
                )
            }
            AnimatedVisibility(state.answered) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackBanner(state.answeredCorrectly)
                    if (!state.answeredCorrectly) {
                        Text(
                            text = stringResource(R.string.correct_answer_is, question.answers[question.correctIndex]),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.feedback.correct,
                        )
                    }
                    state.previousStat?.let {
                        Text(
                            text = stringResource(R.string.question_history, it.correctCount, it.wrongCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun modeTitle(mode: PracticeMode): String = when (mode) {
    PracticeMode.All -> stringResource(R.string.practice_all_topics)
    PracticeMode.Mistakes -> stringResource(R.string.practice_mistakes)
    is PracticeMode.OfTopic -> topicTitle(mode.topic)
}
