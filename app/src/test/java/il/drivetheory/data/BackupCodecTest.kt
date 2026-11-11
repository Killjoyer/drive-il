package il.drivetheory.data

import il.drivetheory.data.backup.BackupCodec
import il.drivetheory.data.backup.BackupFile
import il.drivetheory.data.backup.ExamAnswerRecord
import il.drivetheory.data.backup.ExamRecord
import il.drivetheory.data.backup.InvalidBackupException
import il.drivetheory.data.backup.StatRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupCodecTest {

    private val sample = BackupFile(
        exportedAt = 1_700_000_000_000,
        licenseClass = "B",
        contentLanguage = "ru",
        questionStats = listOf(StatRecord(12, 3, 1, 1_700_000_000_000, true)),
        exams = listOf(
            ExamRecord("B", 1, 2, false, 1, listOf(ExamAnswerRecord(12, 2, listOf(2, 0, 1, 3)), ExamAnswerRecord(13, null, listOf(0, 1, 2, 3)))),
        ),
    )

    @Test
    fun `round trip keeps everything`() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample)))
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val json = BackupCodec.encode(sample).replaceFirst("{", "{\"future_field\":42,")
        assertEquals(sample, BackupCodec.decode(json))
    }

    @Test(expected = InvalidBackupException::class)
    fun `garbage is rejected`() {
        BackupCodec.decode("not json")
    }

    @Test(expected = InvalidBackupException::class)
    fun `foreign json is rejected`() {
        BackupCodec.decode("""{"format":"something.else","version":1,"exported_at":0}""")
    }

    @Test(expected = InvalidBackupException::class)
    fun `newer format version is rejected`() {
        BackupCodec.decode(BackupCodec.encode(sample.copy(version = BackupFile.VERSION + 1)))
    }
}
