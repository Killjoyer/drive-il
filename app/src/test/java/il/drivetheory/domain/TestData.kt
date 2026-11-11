package il.drivetheory.domain

import il.drivetheory.core.model.LicenseClass
import il.drivetheory.core.model.Question
import il.drivetheory.core.model.QuestionStat
import il.drivetheory.core.model.Topic

fun question(id: Int, topic: Topic = Topic.RULES) = Question(
    id = id,
    topic = topic,
    text = "Q$id",
    answers = listOf("a", "b", "c", "d"),
    correctIndex = 0,
    imagePath = null,
    licenses = setOf(LicenseClass.B),
)

fun stat(id: Int, correct: Int, wrong: Int, lastCorrect: Boolean = correct > 0, at: Long = 0) =
    QuestionStat(id, correct, wrong, at, lastCorrect)
