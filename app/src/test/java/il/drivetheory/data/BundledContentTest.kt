package il.drivetheory.data

import il.drivetheory.core.model.LicenseClass
import il.drivetheory.core.model.SignCategory
import il.drivetheory.data.content.ContentAssets
import il.drivetheory.data.content.QuestionsFileDto
import il.drivetheory.data.content.SignCatalogDto
import il.drivetheory.data.content.SignTextsDto
import il.drivetheory.data.content.buildSignSections
import il.drivetheory.data.content.toModel
import il.drivetheory.data.content.withFallback
import il.drivetheory.data.settings.AppLanguage
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validates the generated assets that ship with the app. */
class BundledContentTest {

    private val assets = File("src/main/assets")
    private val json = Json { ignoreUnknownKeys = true }

    private fun languages() = File(assets, ContentAssets.CONTENT_DIR).listFiles()!!
        .filter { File(it, "questions.json").exists() }.map { it.name }

    @Test
    fun `english fallback and russian content are bundled`() {
        assertTrue(AppLanguage.FALLBACK in languages())
        assertTrue("ru" in languages())
    }

    @Test
    fun `every commons sign picture is credited`() {
        val catalog = json.decodeFromString(SignCatalogDto.serializer(), File(assets, ContentAssets.SIGN_CATALOG).readText())
        catalog.signs.flatMap { it.images }.forEach { file ->
            val credit = catalog.credits[file]
            assertTrue("credit of $file", credit != null)
            credit!!
            assertTrue("source of $file", credit.source.startsWith("https://commons.wikimedia.org/wiki/File:"))
            assertTrue("licence of $file", credit.license.isNotBlank())
            if (credit.license.startsWith("CC")) {
                assertTrue("author of $file", !credit.author.isNullOrBlank())
                assertTrue("licence link of $file", credit.licenseUrl?.startsWith("https://creativecommons.org/") == true)
            }
        }
    }

    @Test
    fun `every question is well formed and its picture exists`() {
        for (lang in languages()) {
            val file = json.decodeFromString(QuestionsFileDto.serializer(), File(assets, ContentAssets.questions(lang)).readText())
            val questions = file.questions.map { it.toModel() }
            assertTrue(questions.size > 1500)
            assertEquals(questions.size, questions.map { it.id }.toSet().size)
            questions.forEach { q ->
                assertEquals("answers of ${q.id}", 4, q.answers.size)
                assertTrue("correct index of ${q.id}", q.correctIndex in 0..3)
                assertTrue("text of ${q.id}", q.text.isNotBlank() && q.answers.all { it.isNotBlank() })
                assertTrue("licences of ${q.id}", q.licenses.isNotEmpty())
                q.imagePath?.let { assertTrue("picture $it", File(assets, it).exists()) }
            }
            // Every licence class must have enough questions for an exam.
            LicenseClass.entries.forEach { license -> assertTrue(questions.count { license in it.licenses } >= 30) }
        }
    }

    @Test
    fun `sign catalog is complete for every language`() {
        val catalog = json.decodeFromString(SignCatalogDto.serializer(), File(assets, ContentAssets.SIGN_CATALOG).readText())
        val fallbackTexts = json.decodeFromString(
            SignTextsDto.serializer(), File(assets, ContentAssets.signTexts(AppLanguage.FALLBACK)).readText(),
        )
        for (lang in languages()) {
            val texts = json.decodeFromString(SignTextsDto.serializer(), File(assets, ContentAssets.signTexts(lang)).readText())
                .withFallback(fallbackTexts)
            val sections = buildSignSections(catalog, texts)
            assertEquals(SignCategory.entries.toSet(), sections.map { it.category }.toSet())
            sections.flatMap { it.signs }.forEach { sign ->
                assertTrue("meaning of ${sign.key}", sign.meaning.isNotBlank())
                assertTrue("images of ${sign.key}", sign.images.isNotEmpty())
                sign.images.forEach { assertTrue("image ${it.path}", File(assets, it.path).exists()) }
            }
        }
    }
}
