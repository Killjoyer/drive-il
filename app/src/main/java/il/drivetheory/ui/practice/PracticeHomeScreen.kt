package il.drivetheory.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.drivetheory.R
import il.drivetheory.domain.LearningProgress
import il.drivetheory.ui.components.LoadingBox
import il.drivetheory.ui.components.ProgressLine
import il.drivetheory.ui.components.topicTitle
import il.drivetheory.ui.navigation.PracticeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeHomeScreen(
    onStart: (PracticeMode) -> Unit,
    viewModel: PracticeHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_practice)) }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        when (val s = state) {
            PracticeHomeUiState.Loading -> LoadingBox(Modifier.padding(padding))
            is PracticeHomeUiState.Ready -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { OverallCard(s) }
                item {
                    ModeCard(
                        title = stringResource(R.string.practice_all_topics),
                        subtitle = learnedText(s.overall),
                        progress = s.overall,
                        onClick = { onStart(PracticeMode.All) },
                    )
                }
                item {
                    Text(
                        stringResource(R.string.practice_by_topic),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                items(s.topics, key = { it.topic }) { item ->
                    ModeCard(
                        title = topicTitle(item.topic),
                        subtitle = learnedText(item.progress),
                        progress = item.progress,
                        onClick = { onStart(PracticeMode.OfTopic(item.topic)) },
                    )
                }
                item {
                    ModeCard(
                        title = stringResource(R.string.practice_mistakes),
                        subtitle = if (s.openMistakes == 0) stringResource(R.string.practice_mistakes_none)
                        else pluralStringResource(R.plurals.open_mistakes, s.openMistakes, s.openMistakes),
                        progress = null,
                        enabled = s.openMistakes > 0,
                        onClick = { onStart(PracticeMode.Mistakes) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun learnedText(p: LearningProgress) = stringResource(R.string.learned_of, p.learned, p.total)

@Composable
private fun OverallCard(state: PracticeHomeUiState.Ready) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.practice_overall_title, state.licenseClass.code),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${state.overall.percent}%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            ProgressLine(state.overall, showPercent = false)
            Text(
                stringResource(R.string.practice_overall_hint, state.overall.learned, state.overall.total),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    progress: LearningProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                progress?.let { ProgressLine(it, Modifier.padding(top = 4.dp)) }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
