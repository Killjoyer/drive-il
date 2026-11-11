package il.drivetheory.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.drivetheory.core.model.ImageCredit
import il.drivetheory.data.content.ContentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A credited picture and the sign it illustrates. */
data class CreditEntry(val path: String, val signNumber: String?, val credit: ImageCredit)

@HiltViewModel
class CreditsViewModel @Inject constructor(content: ContentRepository) : ViewModel() {
    /** Every third-party sign picture in catalog order; null while loading. */
    val entries: StateFlow<List<CreditEntry>?> = content.signSections().map { sections ->
        sections.flatMap { it.signs }
            .flatMap { sign -> sign.images.mapNotNull { img -> img.credit?.let { CreditEntry(img.path, sign.number, it) } } }
            .distinctBy { it.path }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
