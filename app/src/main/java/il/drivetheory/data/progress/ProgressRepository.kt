package il.drivetheory.data.progress

import androidx.room.withTransaction
import il.drivetheory.core.model.ExamDetails
import il.drivetheory.core.model.ExamItem
import il.drivetheory.core.model.ExamSummary
import il.drivetheory.core.model.LicenseClass
import il.drivetheory.core.model.Question
import il.drivetheory.core.model.QuestionStat
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A finished exam about to be saved. */
data class CompletedExam(
    val licenseClass: LicenseClass,
    val startedAt: Long,
    val finishedAt: Long,
    val timedOut: Boolean,
    val items: List<ExamItem>,
)

@Singleton
class ProgressRepository @Inject constructor(
    private val db: ProgressDatabase,
    private val clock: Clock,
) {
    private val statDao = db.questionStatDao()
    private val examDao = db.examDao()

    val stats: Flow<Map<Int, QuestionStat>> = statDao.observeAll().map { list ->
        list.associate { it.questionId to it.toModel() }
    }

    val examHistory: Flow<List<ExamSummary>> = examDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun recordAnswer(questionId: Int, correct: Boolean) {
        statDao.recordAnswer(questionId, correct, clock.millis())
    }

    /** Saves the exam and counts its answered questions in the per-question statistics. */
    suspend fun saveExam(exam: CompletedExam): Long = db.withTransaction {
        val id = examDao.insert(
            ExamEntity(
                licenseClass = exam.licenseClass.code,
                startedAt = exam.startedAt,
                finishedAt = exam.finishedAt,
                correctCount = exam.items.count { it.isCorrect },
                questionCount = exam.items.size,
                timedOut = exam.timedOut,
            ),
        )
        examDao.insertAnswers(
            exam.items.mapIndexed { position, item ->
                ExamAnswerEntity(
                    examId = id,
                    position = position,
                    questionId = item.question.id,
                    selectedIndex = item.selectedIndex,
                    optionOrder = item.optionOrder.joinToString(","),
                )
            },
        )
        exam.items.filter { it.isAnswered }.forEach {
            statDao.recordAnswer(it.question.id, it.isCorrect, exam.finishedAt)
        }
        id
    }

    /** Loads a saved exam; questions that are no longer in the pool are skipped. */
    suspend fun examDetails(id: Long, questionsById: Map<Int, Question>): ExamDetails? {
        val exam = examDao.get(id) ?: return null
        val items = examDao.getAnswers(id).mapNotNull { answer ->
            val question = questionsById[answer.questionId] ?: return@mapNotNull null
            ExamItem(
                question = question,
                optionOrder = answer.optionOrder.split(',').mapNotNull(String::toIntOrNull)
                    .takeIf { it.size == question.answers.size } ?: question.answers.indices.toList(),
                selectedIndex = answer.selectedIndex,
            )
        }
        return ExamDetails(exam.toModel(), items)
    }

    suspend fun resetAll() = db.withTransaction {
        statDao.deleteAll()
        examDao.deleteAll()
    }
}

private fun QuestionStatEntity.toModel() =
    QuestionStat(questionId, correctCount, wrongCount, lastAnsweredAt, lastAnswerCorrect)

internal fun ExamEntity.toModel() = ExamSummary(
    id = id,
    licenseClass = LicenseClass.fromCode(licenseClass) ?: LicenseClass.DEFAULT,
    startedAt = startedAt,
    finishedAt = finishedAt,
    correctCount = correctCount,
    questionCount = questionCount,
    timedOut = timedOut,
)
