package il.drivetheory.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import il.drivetheory.R
import il.drivetheory.core.model.Question
import il.drivetheory.core.model.Topic
import il.drivetheory.ui.theme.AppTheme

enum class OptionState { NEUTRAL, SELECTED, CORRECT, WRONG }

@Composable
fun topicTitle(topic: Topic): String = stringResource(
    when (topic) {
        Topic.RULES -> R.string.topic_rules
        Topic.SAFETY -> R.string.topic_safety
        Topic.SIGNS -> R.string.topic_signs
        Topic.VEHICLE -> R.string.topic_vehicle
    },
)

/** Topic chip, question text and (tappable) picture. */
@Composable
fun QuestionHeader(question: Question, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(topicTitle(question.topic)) },
                colors = AssistChipDefaults.assistChipColors(
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.question_number, question.id),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = question.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        question.imagePath?.let { path ->
            TappableImage(path = path, contentDescription = stringResource(R.string.question_image_description))
        }
    }
}

@Composable
fun AnswerOption(
    letter: Int,
    text: String,
    state: OptionState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = AppTheme.feedback
    val scheme = MaterialTheme.colorScheme
    val (container, content, border) = when (state) {
        OptionState.NEUTRAL -> Triple(scheme.surfaceContainerLow, scheme.onSurface, scheme.outlineVariant)
        OptionState.SELECTED -> Triple(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
        OptionState.CORRECT -> Triple(feedback.correctContainer, feedback.onCorrectContainer, feedback.correct)
        OptionState.WRONG -> Triple(feedback.wrongContainer, feedback.onWrongContainer, feedback.wrong)
    }
    val stateText = when (state) {
        OptionState.NEUTRAL -> ""
        OptionState.SELECTED -> stringResource(R.string.option_state_selected)
        OptionState.CORRECT -> stringResource(R.string.option_state_correct)
        OptionState.WRONG -> stringResource(R.string.option_state_wrong)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(if (state == OptionState.NEUTRAL) 1.dp else 2.dp, border),
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = state == OptionState.SELECTED,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { if (stateText.isNotEmpty()) stateDescription = stateText },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = CircleShape, color = border.copy(alpha = 0.15f), modifier = Modifier.size(30.dp)) {}
                when (state) {
                    OptionState.CORRECT -> Icon(Icons.Filled.Check, null, tint = feedback.correct)
                    OptionState.WRONG -> Icon(Icons.Filled.Close, null, tint = feedback.wrong)
                    else -> Text(
                        text = stringResource(R.string.option_letters).getOrElse(letter) { '?' }.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (state == OptionState.SELECTED) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

/** Small coloured status line, e.g. "Верно!" / "Неверно". */
@Composable
fun FeedbackBanner(correct: Boolean, modifier: Modifier = Modifier) {
    val feedback = AppTheme.feedback
    Surface(
        color = if (correct) feedback.correctContainer else feedback.wrongContainer,
        contentColor = if (correct) feedback.onCorrectContainer else feedback.onWrongContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (correct) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (correct) feedback.correct else feedback.wrong,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(if (correct) R.string.feedback_correct else R.string.feedback_wrong),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}
