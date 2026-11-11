package il.drivetheory.core.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Format of the computerized theory test of the Israeli Ministry of Transport:
 * 30 multiple-choice questions (4 options, one correct) drawn from the official pool for the
 * candidate's licence class, 40 minutes in total, passed with at least 26 correct answers
 * (i.e. at most 4 mistakes). Unanswered questions count as mistakes.
 */
object ExamRules {
    const val QUESTION_COUNT = 30
    const val PASS_MIN_CORRECT = 26
    const val MAX_MISTAKES = QUESTION_COUNT - PASS_MIN_CORRECT
    val DURATION: Duration = 40.minutes
}

/** One question of a finished exam, with the options in the order they were shown. */
data class ExamItem(
    val question: Question,
    /** displayed position -> original answer index */
    val optionOrder: List<Int>,
    /** original answer index chosen by the user, null when unanswered */
    val selectedIndex: Int?,
) {
    val isCorrect: Boolean get() = selectedIndex == question.correctIndex
    val isAnswered: Boolean get() = selectedIndex != null
}

data class ExamSummary(
    val id: Long,
    val licenseClass: LicenseClass,
    val startedAt: Long,
    val finishedAt: Long,
    val correctCount: Int,
    val questionCount: Int,
    val timedOut: Boolean,
) {
    val passed: Boolean get() = questionCount == ExamRules.QUESTION_COUNT && correctCount >= ExamRules.PASS_MIN_CORRECT
    val mistakes: Int get() = questionCount - correctCount
    val durationMillis: Long get() = finishedAt - startedAt
}

data class ExamDetails(val summary: ExamSummary, val items: List<ExamItem>)
