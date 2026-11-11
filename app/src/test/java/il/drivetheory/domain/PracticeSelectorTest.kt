package il.drivetheory.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeSelectorTest {

    private val pool = (1..100).map { question(it) }

    @Test
    fun `empty pool gives nothing`() {
        assertNull(PracticeSelector().next(emptyList(), emptyMap()))
    }

    @Test
    fun `mostly picks questions never answered correctly`() {
        // Half the pool learned.
        val stats = (1..50).associateWith { stat(it, correct = 1, wrong = 0) }
        val selector = PracticeSelector(Random(42))
        val picks = (1..2000).map { selector.next(pool, stats)!!.id }
        val unlearnedShare = picks.count { it > 50 } / picks.size.toDouble()
        assertTrue("unlearned share $unlearnedShare", unlearnedShare in 0.75..0.85)
    }

    @Test
    fun `sometimes repeats learned questions`() {
        val stats = (1..50).associateWith { stat(it, correct = 3, wrong = 0) }
        val selector = PracticeSelector(Random(7))
        assertTrue((1..500).any { selector.next(pool, stats)!!.id <= 50 })
    }

    @Test
    fun `wrongly answered questions come back more often than unseen ones`() {
        val stats = (1..10).associateWith { stat(it, correct = 0, wrong = 3, lastCorrect = false) }
        val selector = PracticeSelector(Random(3))
        val picks = (1..5000).map { selector.next(pool, stats)!!.id }
        val perMistake = picks.count { it <= 10 } / 10.0
        val perUnseen = picks.count { it > 10 } / 90.0
        assertTrue("mistakes $perMistake vs unseen $perUnseen", perMistake > 2 * perUnseen)
    }

    @Test
    fun `everything learned still yields questions`() {
        val stats = pool.associate { it.id to stat(it.id, correct = 1, wrong = 0) }
        assertNotNull(PracticeSelector().next(pool, stats))
    }

    @Test
    fun `recent questions are not repeated`() {
        val small = (1..12).map { question(it) }
        val recent = (1..10).toList()
        val selector = PracticeSelector(Random(1))
        repeat(200) { assertTrue(selector.next(small, emptyMap(), recent)!!.id in 11..12) }
    }

    @Test
    fun `single question pool still works with recent list`() {
        val one = listOf(question(1))
        assertEquals(1, PracticeSelector().next(one, emptyMap(), listOf(1))!!.id)
    }

    @Test
    fun `progress counts questions answered correctly at least once`() {
        val stats = mapOf(1 to stat(1, 1, 5, lastCorrect = false), 2 to stat(2, 0, 2), 3 to stat(3, 2, 0))
        val progress = LearningProgress.of(pool, stats)
        assertEquals(2, progress.learned)
        assertEquals(100, progress.total)
        assertEquals(2, progress.percent)
    }
}
