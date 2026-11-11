package il.drivetheory.ui.navigation

import il.drivetheory.core.model.Topic
import kotlinx.serialization.Serializable

@Serializable data object PracticeHomeRoute
@Serializable data class PracticeSessionRoute(val mode: String)
@Serializable data object ExamHomeRoute
@Serializable data object ExamSessionRoute
@Serializable data class ExamResultRoute(val examId: Long)
@Serializable data object SignsRoute
@Serializable data object SettingsRoute
@Serializable data object AboutRoute
@Serializable data object CreditsRoute

/** What a practice session draws questions from. */
sealed interface PracticeMode {
    data object All : PracticeMode
    data object Mistakes : PracticeMode
    data class OfTopic(val topic: Topic) : PracticeMode

    fun encode(): String = when (this) {
        All -> "ALL"
        Mistakes -> "MISTAKES"
        is OfTopic -> topic.name
    }

    companion object {
        fun decode(value: String): PracticeMode = when (value) {
            "ALL" -> All
            "MISTAKES" -> Mistakes
            else -> OfTopic(Topic.valueOf(value))
        }
    }
}
