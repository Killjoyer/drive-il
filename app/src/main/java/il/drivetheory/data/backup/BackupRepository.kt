package il.drivetheory.data.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import il.drivetheory.core.model.LicenseClass
import il.drivetheory.data.progress.ExamAnswerEntity
import il.drivetheory.data.progress.ExamEntity
import il.drivetheory.data.progress.ProgressDatabase
import il.drivetheory.data.progress.QuestionStatEntity
import il.drivetheory.data.settings.SettingsRepository
import il.drivetheory.di.IoDispatcher
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class ImportSummary(val questionCount: Int, val examCount: Int)

/** Exports / imports the local progress to a user-chosen file (Storage Access Framework). */
@Singleton
class BackupRepository @Inject constructor(
    private val db: ProgressDatabase,
    private val settings: SettingsRepository,
    private val contentResolver: ContentResolver,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun export(target: Uri) = withContext(io) {
        val file = snapshot()
        val out = contentResolver.openOutputStream(target, "wt") ?: error("Cannot open $target")
        out.bufferedWriter(Charsets.UTF_8).use { it.write(BackupCodec.encode(file)) }
    }

    /** Replaces all local progress with the content of [source]. */
    suspend fun import(source: Uri): ImportSummary = withContext(io) {
        val text = contentResolver.openInputStream(source)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Cannot open $source")
        restore(BackupCodec.decode(text))
    }

    internal suspend fun snapshot(): BackupFile {
        val answersByExam = db.examDao().getAllAnswers().groupBy { it.examId }
        return BackupFile(
            exportedAt = clock.millis(),
            licenseClass = settings.currentLicenseClass().code,
            contentLanguage = settings.currentLanguage(),
            questionStats = db.questionStatDao().getAll().map {
                StatRecord(it.questionId, it.correctCount, it.wrongCount, it.lastAnsweredAt, it.lastAnswerCorrect)
            },
            exams = db.examDao().getAll().map { exam ->
                ExamRecord(
                    licenseClass = exam.licenseClass,
                    startedAt = exam.startedAt,
                    finishedAt = exam.finishedAt,
                    timedOut = exam.timedOut,
                    correct = exam.correctCount,
                    answers = answersByExam[exam.id].orEmpty().map { a ->
                        ExamAnswerRecord(a.questionId, a.selectedIndex, a.optionOrder.split(',').mapNotNull(String::toIntOrNull))
                    },
                )
            },
        )
    }

    internal suspend fun restore(file: BackupFile): ImportSummary {
        db.withTransaction {
            val statDao = db.questionStatDao()
            val examDao = db.examDao()
            statDao.deleteAll()
            examDao.deleteAll()
            statDao.upsertAll(
                file.questionStats.map {
                    QuestionStatEntity(it.id, it.correct, it.wrong, it.lastAnsweredAt, it.lastAnswerCorrect)
                },
            )
            file.exams.forEach { exam ->
                val id = examDao.insert(
                    ExamEntity(
                        licenseClass = exam.licenseClass,
                        startedAt = exam.startedAt,
                        finishedAt = exam.finishedAt,
                        correctCount = exam.correct.coerceIn(0, exam.answers.size),
                        questionCount = exam.answers.size,
                        timedOut = exam.timedOut,
                    ),
                )
                examDao.insertAnswers(
                    exam.answers.mapIndexed { position, a ->
                        ExamAnswerEntity(id, position, a.question, a.selected, a.order.joinToString(","))
                    },
                )
            }
        }
        file.licenseClass?.let(LicenseClass::fromCode)?.let { settings.setLicenseClass(it) }
        return ImportSummary(file.questionStats.size, file.exams.size)
    }
}
