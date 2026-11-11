package il.drivetheory.core.model

/** Topic sections of the official question pool. */
enum class Topic { RULES, SAFETY, SIGNS, VEHICLE }

/** Licence classes the official pool distinguishes. [code] is the code used in the pool data. */
enum class LicenseClass(val code: String) {
    B("B"), A("A"), TRACTOR("1"), C1("C1"), C("C"), D("D");

    companion object {
        val DEFAULT = B
        fun fromCode(code: String): LicenseClass? = entries.firstOrNull { it.code == code }
    }
}

data class Question(
    val id: Int,
    val topic: Topic,
    val text: String,
    val answers: List<String>,
    val correctIndex: Int,
    /** Asset path of the picture, if the question has one. */
    val imagePath: String?,
    val licenses: Set<LicenseClass>,
)

/** Per-question answer history kept on the device. */
data class QuestionStat(
    val questionId: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val lastAnsweredAt: Long,
    val lastAnswerCorrect: Boolean,
) {
    /** A question counts as learned once it has been answered correctly at least once. */
    val isLearned: Boolean get() = correctCount > 0
    val isOpenMistake: Boolean get() = wrongCount > 0 && !lastAnswerCorrect
}

enum class SignCategory {
    WARNING, MANDATORY, PRIORITY, PROHIBITORY, PUBLIC_TRANSPORT,
    INFORMATION, TRAFFIC_LIGHTS, ROAD_MARKINGS, ROADWORKS,
}

data class RoadSign(
    val key: String,
    /** Official sign number, null for entries without one (some road markings). */
    val number: String?,
    val category: SignCategory,
    val images: List<SignImage>,
    val meaning: String,
)

/** A sign picture (asset path) and, for third-party artwork, how it must be credited. */
data class SignImage(val path: String, val credit: ImageCredit?)

/**
 * Attribution of a third-party image following the Creative Commons "TASL" practice:
 * title, author, source and licence, each linked where a link exists.
 */
data class ImageCredit(
    val title: String,
    val author: String?,
    val authorUrl: String?,
    val license: String,
    val licenseUrl: String?,
    val sourceUrl: String,
)

data class SignSection(
    val category: SignCategory,
    val title: String,
    val signs: List<RoadSign>,
)
