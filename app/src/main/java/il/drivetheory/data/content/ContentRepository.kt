package il.drivetheory.data.content

import android.content.res.AssetManager
import il.drivetheory.core.model.Question
import il.drivetheory.core.model.SignSection
import il.drivetheory.data.settings.SettingsRepository
import il.drivetheory.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/** Read-only access to the bundled question pool and sign catalog (parsed once, kept in memory). */
@Singleton
class ContentRepository @Inject constructor(
    private val assets: AssetManager,
    private val settings: SettingsRepository,
    private val json: Json,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val questionsCache = mutableMapOf<String, List<Question>>()
    private val signsCache = mutableMapOf<String, List<SignSection>>()

    /** Languages for which content is bundled (folders under assets/content). */
    suspend fun availableLanguages(): List<String> = withContext(io) {
        assets.list(ContentAssets.CONTENT_DIR).orEmpty().filter { lang ->
            assets.list("${ContentAssets.CONTENT_DIR}/$lang").orEmpty().contains("questions.json")
        }.sorted()
    }

    suspend fun allQuestions(language: String): List<Question> = mutex.withLock {
        questionsCache.getOrPut(language) {
            read(ContentAssets.questions(language), QuestionsFileDto.serializer())
                .questions.map { it.toModel() }
        }
    }

    suspend fun signSections(language: String): List<SignSection> = mutex.withLock {
        signsCache.getOrPut(language) {
            buildSignSections(
                read(ContentAssets.SIGN_CATALOG, SignCatalogDto.serializer()),
                read(ContentAssets.signTexts(language), SignTextsDto.serializer()),
            )
        }
    }

    /** Questions of the selected licence class in the selected language; re-emits on changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun questionPool(): Flow<List<Question>> =
        combine(settings.contentLanguage, settings.licenseClass, ::Pair).flatMapLatest { (lang, license) ->
            flow { emit(allQuestions(lang).filter { license in it.licenses }) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun signSections(): Flow<List<SignSection>> =
        settings.contentLanguage.flatMapLatest { lang -> flow { emit(signSections(lang)) } }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private suspend fun <T> read(path: String, deserializer: DeserializationStrategy<T>): T =
        withContext(io) { assets.open(path).use { json.decodeFromStream(deserializer, it) } }
}
