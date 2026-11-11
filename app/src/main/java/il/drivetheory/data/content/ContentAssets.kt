package il.drivetheory.data.content

import il.drivetheory.core.model.LicenseClass
import il.drivetheory.core.model.ImageCredit
import il.drivetheory.core.model.Question
import il.drivetheory.core.model.RoadSign
import il.drivetheory.core.model.SignCategory
import il.drivetheory.core.model.SignImage
import il.drivetheory.core.model.SignSection
import il.drivetheory.core.model.Topic

/** Asset layout shared by the content pipeline and the app. */
object ContentAssets {
    const val CONTENT_DIR = "content"
    fun questions(language: String) = "$CONTENT_DIR/$language/questions.json"
    const val SIGN_CATALOG = "$CONTENT_DIR/signs.json"
    fun signTexts(language: String) = "$CONTENT_DIR/$language/signs.json"
    fun questionImage(name: String) = "img/q/$name.webp"
    fun signImage(file: String) = "img/s/$file"
}

internal fun QuestionDto.toModel() = Question(
    id = id,
    topic = Topic.valueOf(topic),
    text = text,
    answers = answers,
    correctIndex = correct,
    imagePath = image?.let(ContentAssets::questionImage),
    licenses = licenses.mapNotNullTo(mutableSetOf(), LicenseClass::fromCode),
)

internal fun CreditDto.toModel() = ImageCredit(title, author, authorUrl, license, licenseUrl, source)

/** [texts] of the selected language completed with [fallback] for anything it lacks. */
internal fun SignTextsDto.withFallback(fallback: SignTextsDto?): SignTextsDto =
    if (fallback == null) this
    else SignTextsDto(fallback.categories + categories, fallback.meanings + meanings)

/** Questions of the selected language; ids it lacks are taken from [fallback]. */
internal fun List<Question>.withFallback(fallback: List<Question>?): List<Question> {
    if (fallback == null) return this
    val own = mapTo(mutableSetOf()) { it.id }
    return (this + fallback.filter { it.id !in own }).sortedBy { it.id }
}

internal fun buildSignSections(catalog: SignCatalogDto, texts: SignTextsDto): List<SignSection> {
    val byCategory = catalog.signs.groupBy { it.category }
    return catalog.categories.mapNotNull { key ->
        val signs = byCategory[key].orEmpty().map { dto ->
            RoadSign(
                key = dto.key,
                number = dto.number,
                category = SignCategory.valueOf(dto.category),
                images = dto.images.map { file ->
                    SignImage(ContentAssets.signImage(file), catalog.credits[file]?.toModel())
                } + listOfNotNull(dto.questionImage?.let { SignImage(ContentAssets.questionImage(it), null) }),
                meaning = texts.meanings[dto.key].orEmpty(),
            )
        }
        if (signs.isEmpty()) null
        else SignSection(SignCategory.valueOf(key), texts.categories[key] ?: key, signs)
    }
}
