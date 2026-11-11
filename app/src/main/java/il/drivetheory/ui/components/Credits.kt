package il.drivetheory.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import il.drivetheory.core.model.ImageCredit

/**
 * One-line TASL attribution — "Title · Author · Licence" — with the title linked to the source
 * page, the author to their profile and the licence to its deed, where those links exist.
 * Links open in the browser (the app itself has no internet access).
 */
@Composable
fun CreditText(
    credit: ImageCredit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    val links = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    Text(creditString(credit, links), modifier, style = style, color = color)
}

internal fun creditString(credit: ImageCredit, links: TextLinkStyles): AnnotatedString = buildAnnotatedString {
    fun part(text: String, url: String?) {
        if (url == null) append(text) else withLink(LinkAnnotation.Url(url, links)) { append(text) }
    }
    part(credit.title, credit.sourceUrl)
    credit.author?.let {
        append(" · ")
        part(it, credit.authorUrl)
    }
    append(" · ")
    part(credit.license, credit.licenseUrl)
}
