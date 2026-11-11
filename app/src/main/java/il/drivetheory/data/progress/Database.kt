package il.drivetheory.data.progress

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "question_stats")
data class QuestionStatEntity(
    @PrimaryKey @ColumnInfo(name = "question_id") val questionId: Int,
    @ColumnInfo(name = "correct_count") val correctCount: Int,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int,
    @ColumnInfo(name = "last_answered_at") val lastAnsweredAt: Long,
    @ColumnInfo(name = "last_answer_correct") val lastAnswerCorrect: Boolean,
)

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "license_class") val licenseClass: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    @ColumnInfo(name = "correct_count") val correctCount: Int,
    @ColumnInfo(name = "question_count") val questionCount: Int,
    @ColumnInfo(name = "timed_out") val timedOut: Boolean,
)

@Entity(
    tableName = "exam_answers",
    primaryKeys = ["exam_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["id"],
            childColumns = ["exam_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExamAnswerEntity(
    @ColumnInfo(name = "exam_id") val examId: Long,
    val position: Int,
    @ColumnInfo(name = "question_id") val questionId: Int,
    /** Original index of the chosen answer, null when unanswered. */
    @ColumnInfo(name = "selected_index") val selectedIndex: Int?,
    /** Comma-separated original indexes in displayed order, e.g. "2,0,3,1". */
    @ColumnInfo(name = "option_order") val optionOrder: String,
)

@Dao
interface QuestionStatDao {
    @Query("SELECT * FROM question_stats")
    fun observeAll(): Flow<List<QuestionStatEntity>>

    @Query("SELECT * FROM question_stats")
    suspend fun getAll(): List<QuestionStatEntity>

    @Query("SELECT * FROM question_stats WHERE question_id = :id")
    suspend fun get(id: Int): QuestionStatEntity?

    @Upsert
    suspend fun upsert(stat: QuestionStatEntity)

    @Upsert
    suspend fun upsertAll(stats: List<QuestionStatEntity>)

    @Query("DELETE FROM question_stats")
    suspend fun deleteAll()

    @Transaction
    suspend fun recordAnswer(questionId: Int, correct: Boolean, at: Long) {
        val current = get(questionId)
        upsert(
            QuestionStatEntity(
                questionId = questionId,
                correctCount = (current?.correctCount ?: 0) + if (correct) 1 else 0,
                wrongCount = (current?.wrongCount ?: 0) + if (correct) 0 else 1,
                lastAnsweredAt = at,
                lastAnswerCorrect = correct,
            ),
        )
    }
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY finished_at DESC")
    fun observeAll(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams ORDER BY finished_at DESC")
    suspend fun getAll(): List<ExamEntity>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun get(id: Long): ExamEntity?

    @Query("SELECT * FROM exam_answers WHERE exam_id = :examId ORDER BY position")
    suspend fun getAnswers(examId: Long): List<ExamAnswerEntity>

    @Query("SELECT * FROM exam_answers ORDER BY exam_id, position")
    suspend fun getAllAnswers(): List<ExamAnswerEntity>

    @Insert
    suspend fun insert(exam: ExamEntity): Long

    @Insert
    suspend fun insertAnswers(answers: List<ExamAnswerEntity>)

    @Query("DELETE FROM exams")
    suspend fun deleteAll()
}

@Database(
    entities = [QuestionStatEntity::class, ExamEntity::class, ExamAnswerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun questionStatDao(): QuestionStatDao
    abstract fun examDao(): ExamDao

    companion object {
        const val NAME = "progress.db"
    }
}
