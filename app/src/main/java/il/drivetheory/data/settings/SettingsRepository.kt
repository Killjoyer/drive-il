package il.drivetheory.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import il.drivetheory.core.model.LicenseClass
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val licenseClass: Flow<LicenseClass> = dataStore.data
        .map { prefs -> prefs[KEY_LICENSE]?.let(LicenseClass::fromCode) ?: LicenseClass.DEFAULT }
        .distinctUntilChanged()

    /** Language of the question pool / sign meanings. Only Russian is bundled for now. */
    val contentLanguage: Flow<String> = dataStore.data
        .map { prefs -> prefs[KEY_LANGUAGE] ?: DEFAULT_LANGUAGE }
        .distinctUntilChanged()

    suspend fun currentLicenseClass(): LicenseClass = licenseClass.first()
    suspend fun currentLanguage(): String = contentLanguage.first()

    suspend fun setLicenseClass(value: LicenseClass) {
        dataStore.edit { it[KEY_LICENSE] = value.code }
    }

    suspend fun setContentLanguage(language: String) {
        dataStore.edit { it[KEY_LANGUAGE] = language }
    }

    companion object {
        const val DEFAULT_LANGUAGE = "ru"
        private val KEY_LICENSE = stringPreferencesKey("license_class")
        private val KEY_LANGUAGE = stringPreferencesKey("content_language")
    }
}
