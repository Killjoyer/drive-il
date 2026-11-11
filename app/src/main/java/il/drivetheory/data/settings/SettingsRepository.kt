package il.drivetheory.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import il.drivetheory.core.model.LicenseClass
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext context: Context,
) {
    private val languagePrefs = AppLanguage.prefs(context)

    val licenseClass: Flow<LicenseClass> = dataStore.data
        .map { prefs -> prefs[KEY_LICENSE]?.let(LicenseClass::fromCode) ?: LicenseClass.DEFAULT }
        .distinctUntilChanged()

    /** App language (UI and content), see [AppLanguage]. */
    val language: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == AppLanguage.KEY) trySend(prefs.getString(key, null) ?: AppLanguage.DEFAULT)
        }
        languagePrefs.registerOnSharedPreferenceChangeListener(listener)
        send(languagePrefs.getString(AppLanguage.KEY, null) ?: AppLanguage.DEFAULT)
        awaitClose { languagePrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    suspend fun currentLicenseClass(): LicenseClass = licenseClass.first()
    fun currentLanguage(): String = languagePrefs.getString(AppLanguage.KEY, null) ?: AppLanguage.DEFAULT

    suspend fun setLicenseClass(value: LicenseClass) {
        dataStore.edit { it[KEY_LICENSE] = value.code }
    }

    fun setLanguage(language: String) {
        languagePrefs.edit().putString(AppLanguage.KEY, language).apply()
    }

    companion object {
        private val KEY_LICENSE = stringPreferencesKey("license_class")
    }
}
