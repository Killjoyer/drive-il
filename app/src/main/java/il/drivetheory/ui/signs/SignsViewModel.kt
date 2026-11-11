package il.drivetheory.ui.signs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.drivetheory.core.model.SignCategory
import il.drivetheory.core.model.SignSection
import il.drivetheory.data.content.ContentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SignsUiState(
    val loading: Boolean = true,
    /** All categories in official order (for the filter chips). */
    val categories: List<SignSection> = emptyList(),
    /** Sections after applying the category filter and search. */
    val sections: List<SignSection> = emptyList(),
    val selectedCategory: SignCategory? = null,
    val query: String = "",
)

@HiltViewModel
class SignsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    content: ContentRepository,
) : ViewModel() {

    private val query = savedState.getStateFlow(KEY_QUERY, "")
    private val category = savedState.getStateFlow<String?>(KEY_CATEGORY, null)

    val uiState: StateFlow<SignsUiState> = combine(content.signSections(), query, category) { all, q, cat ->
        val selected = cat?.let(SignCategory::valueOf)
        val needle = q.trim()
        val numeric = needle.all(Char::isDigit)
        SignsUiState(
            loading = false,
            categories = all,
            sections = all
                .filter { selected == null || it.category == selected }
                .map { section ->
                    if (needle.isEmpty()) section
                    else section.copy(
                        signs = section.signs.filter {
                            // A number searches sign numbers only; anything else searches the meanings too.
                            it.number?.startsWith(needle) == true ||
                                (!numeric && it.meaning.contains(needle, ignoreCase = true))
                        },
                    )
                }
                .filter { it.signs.isNotEmpty() },
            selectedCategory = selected,
            query = q,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SignsUiState())

    fun setQuery(value: String) {
        savedState[KEY_QUERY] = value
    }

    fun selectCategory(value: SignCategory?) {
        savedState[KEY_CATEGORY] = value?.name
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_CATEGORY = "category"
    }
}
