package il.drivetheory

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import il.drivetheory.data.settings.AppLanguage
import il.drivetheory.data.settings.SettingsRepository
import il.drivetheory.ui.components.AssetImageLoader
import il.drivetheory.ui.components.LocalAssetImageLoader
import il.drivetheory.ui.navigation.AppNavHost
import il.drivetheory.ui.theme.DriveTheoryTheme
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var imageLoader: AssetImageLoader
    @Inject lateinit var settings: SettingsRepository

    /** Language this activity's resources were created with. */
    private lateinit var uiLanguage: String

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        uiLanguage = AppLanguage.read(newBase)
        applyOverrideConfiguration(AppLanguage.configurationFor(uiLanguage))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // A language change (in settings) is applied by recreating the activity.
                settings.language.collect { if (it != uiLanguage) recreate() }
            }
        }
        setContent {
            DriveTheoryTheme {
                CompositionLocalProvider(LocalAssetImageLoader provides imageLoader) {
                    AppNavHost()
                }
            }
        }
    }
}
