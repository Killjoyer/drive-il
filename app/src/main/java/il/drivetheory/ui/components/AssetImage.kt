package il.drivetheory.ui.components

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import il.drivetheory.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Minimal image loader for bundled WebP assets: decodes off the main thread and keeps a
 * size-bounded memory cache. All pictures are small, so no extra image library is needed.
 */
@Singleton
class AssetImageLoader @Inject constructor(
    private val assets: AssetManager,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(path: String): ImageBitmap? = cache.get(path)

    suspend fun load(path: String): ImageBitmap? = cache.get(path) ?: withContext(io) {
        runCatching {
            assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull()?.also { cache.put(path, it) }
    }

    private companion object {
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}

val LocalAssetImageLoader = staticCompositionLocalOf<AssetImageLoader> {
    error("AssetImageLoader not provided")
}

@Composable
fun AssetImage(
    path: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    /** Size the image to its own aspect ratio within the incoming constraints (e.g. full width). */
    keepAspectRatio: Boolean = false,
) {
    val loader = LocalAssetImageLoader.current
    val bitmap by produceState(initialValue = loader.cached(path), path) { value = loader.load(path) }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = contentDescription,
            modifier = if (keepAspectRatio) modifier.aspectRatio(image.width.toFloat() / image.height) else modifier,
            contentScale = contentScale,
        )
    } else {
        Box(modifier.then(if (keepAspectRatio) Modifier.aspectRatio(PLACEHOLDER_RATIO) else Modifier).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

private const val PLACEHOLDER_RATIO = 4f / 3f
