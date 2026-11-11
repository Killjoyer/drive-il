package il.drivetheory.ui.signs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.drivetheory.R
import il.drivetheory.core.model.RoadSign
import il.drivetheory.core.model.SignImage
import il.drivetheory.ui.components.AssetImage
import il.drivetheory.ui.components.EmptyState
import il.drivetheory.ui.components.LoadingBox
import il.drivetheory.ui.components.ZoomImageDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SignsScreen(onOpenCredits: () -> Unit, viewModel: SignsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(state.selectedCategory) { listState.scrollToItem(0) }
    var zoomed by remember { mutableStateOf<Pair<SignImage, String>?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_signs)) }) }) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.signs_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, stringResource(R.string.action_clear))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text(stringResource(R.string.signs_all)) },
                    )
                }
                items(state.categories, key = { it.category }) { section ->
                    FilterChip(
                        selected = state.selectedCategory == section.category,
                        onClick = { viewModel.selectCategory(section.category) },
                        label = { Text(section.title) },
                    )
                }
            }
            if (state.sections.isEmpty()) {
                EmptyState(stringResource(R.string.signs_nothing_found_title), stringResource(R.string.signs_nothing_found))
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                state.sections.forEach { section ->
                    stickyHeader(key = "h_${section.category}") {
                        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${section.title} (${section.signs.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                    items(section.signs, key = { it.key }) { sign ->
                        SignRow(sign, onZoom = { image -> zoomed = image to sign.meaning })
                    }
                }
                if (state.sections.isNotEmpty()) {
                    item(key = "credits") {
                        TextButton(onClick = onOpenCredits, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stringResource(R.string.signs_images_credit),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
    zoomed?.let { (image, description) -> ZoomImageDialog(image.path, description, image.credit) { zoomed = null } }
}

@Composable
private fun SignRow(sign: RoadSign, onZoom: (SignImage) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                sign.images.take(2).forEachIndexed { i, image ->
                    if (i > 0) Spacer(Modifier.height(6.dp))
                    AssetImage(
                        path = image.path,
                        contentDescription = stringResource(R.string.sign_image_description, sign.number ?: ""),
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(8.dp))
                            // Sign artwork is transparent and drawn for a white background (also in dark theme).
                            .background(Color.White)
                            .clickable(onClickLabel = stringResource(R.string.image_zoom_action)) { onZoom(image) },
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sign.number?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.sign_number, it),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(sign.meaning, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
