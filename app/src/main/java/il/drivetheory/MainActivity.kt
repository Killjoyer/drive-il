package il.drivetheory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import il.drivetheory.ui.components.AssetImageLoader
import il.drivetheory.ui.components.LocalAssetImageLoader
import il.drivetheory.ui.navigation.AppNavHost
import il.drivetheory.ui.theme.DriveTheoryTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var imageLoader: AssetImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DriveTheoryTheme {
                CompositionLocalProvider(LocalAssetImageLoader provides imageLoader) {
                    AppNavHost()
                }
            }
        }
    }
}
