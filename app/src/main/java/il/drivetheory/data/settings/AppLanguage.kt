package il.drivetheory.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

/**
 * The app language, used for both the UI and the bundled content.
 *
 * It is kept in [SharedPreferences] rather than DataStore because the activity must read it
 * synchronously in `attachBaseContext`, before anything is drawn.
 */
object AppLanguage {
    /** Used on first start and whenever the stored language is not bundled. */
    const val DEFAULT = "en"

    /** Every text missing in the selected language is taken from this language. */
    const val FALLBACK = "en"

    private const val PREFS = "app_language"
    internal const val KEY = "language"

    internal fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context): String = prefs(context).getString(KEY, null) ?: DEFAULT

    /** Overrides the locale of an activity's resources; other configuration (dark mode, ...) stays as is. */
    fun configurationFor(language: String): Configuration =
        Configuration().apply { setLocale(Locale.forLanguageTag(language)) }
}
