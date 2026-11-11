package il.drivetheory.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Versioned, human-readable export file. Only progress is stored — content ships with the app. */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    @SerialName("exported_at") val exportedAt: Long,
    @SerialName("license_class") val licenseClass: String? = null,
    @SerialName("content_language") val contentLanguage: String? = null,
    @SerialName("question_stats") val questionStats: List<StatRecord> = emptyList(),
    val exams: List<ExamRecord> = emptyList(),
) {
    companion object {
        const val FORMAT = "il.drivetheory.progress"
        const val VERSION = 1
    }
}

@Serializable
data class StatRecord(
    val id: Int,
    val correct: Int,
    val wrong: Int,
    @SerialName("last_at") val lastAnsweredAt: Long,
    @SerialName("last_correct") val lastAnswerCorrect: Boolean,
)

@Serializable
data class ExamRecord(
    @SerialName("license_class") val licenseClass: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("finished_at") val finishedAt: Long,
    @SerialName("timed_out") val timedOut: Boolean,
    val correct: Int,
    val answers: List<ExamAnswerRecord>,
)

@Serializable
data class ExamAnswerRecord(
    val question: Int,
    val selected: Int? = null,
    val order: List<Int>,
)

class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

object BackupCodec {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw InvalidBackupException("Not a progress file", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("Not a progress file", e)
        }
        if (file.format != BackupFile.FORMAT) throw InvalidBackupException("Unknown format ${file.format}")
        if (file.version > BackupFile.VERSION) throw InvalidBackupException("Newer version ${file.version}")
        file.questionStats.forEach {
            if (it.correct < 0 || it.wrong < 0) throw InvalidBackupException("Negative counters for ${it.id}")
        }
        return file
    }
}
