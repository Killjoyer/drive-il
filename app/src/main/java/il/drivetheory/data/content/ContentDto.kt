package il.drivetheory.data.content

import kotlinx.serialization.Serializable

/** Wire format of the JSON files produced by tools/pipeline/build_content.py. */

@Serializable
data class QuestionsFileDto(
    val language: String,
    val source: String,
    val questions: List<QuestionDto>,
)

@Serializable
data class QuestionDto(
    val id: Int,
    val topic: String,
    val text: String,
    val answers: List<String>,
    val correct: Int,
    val licenses: List<String>,
    val image: String? = null,
)

@Serializable
data class SignCatalogDto(
    val categories: List<String>,
    val signs: List<SignDto>,
)

@Serializable
data class SignDto(
    val key: String,
    val number: String? = null,
    val category: String,
    val images: List<String> = emptyList(),
    val questionImage: String? = null,
    val questionId: Int? = null,
)

@Serializable
data class SignTextsDto(
    val categories: Map<String, String>,
    val meanings: Map<String, String>,
)
