package il.drivetheory.domain

import il.drivetheory.core.model.ExamRules
import il.drivetheory.core.model.Topic
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamGeneratorTest {

    // Shares of the real licence-B pool: 503 rules, 306 safety, 362 signs, 106 vehicle.
    private val pool = (1..503).map { question(it, Topic.RULES) } +
        (1001..1306).map { question(it, Topic.SAFETY) } +
        (2001..2362).map { question(it, Topic.SIGNS) } +
        (3001..3106).map { question(it, Topic.VEHICLE) }

    @Test
    fun `exam has 30 distinct questions`() {
        repeat(50) { seed ->
            val exam = ExamGenerator(Random(seed)).generate(pool)
            assertEquals(ExamRules.QUESTION_COUNT, exam.size)
            assertEquals(exam.size, exam.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `topics are represented proportionally`() {
        val quotas = ExamGenerator().topicQuotas(pool.groupBy { it.topic }.mapValues { it.value.size }, 30)
        assertEquals(30, quotas.values.sum())
        assertEquals(mapOf(Topic.RULES to 12, Topic.SAFETY to 7, Topic.SIGNS to 9, Topic.VEHICLE to 2), quotas)
        val exam = ExamGenerator(Random(1)).generate(pool)
        assertEquals(quotas, exam.groupingBy { it.topic }.eachCount())
    }

    @Test
    fun `quota never exceeds the questions available in a topic`() {
        val small = (1..100).map { question(it, Topic.RULES) } + listOf(question(500, Topic.VEHICLE))
        val quotas = ExamGenerator().topicQuotas(small.groupBy { it.topic }.mapValues { it.value.size }, 30)
        assertEquals(30, quotas.values.sum())
        assertTrue((quotas[Topic.VEHICLE] ?: 0) <= 1)
    }

    @Test
    fun `small pool returns everything`() {
        val small = (1..10).map { question(it) }
        assertEquals(10, ExamGenerator().generate(small).size)
    }

    @Test
    fun `option order is a permutation`() {
        repeat(20) { assertEquals(listOf(0, 1, 2, 3), shuffledOptionOrder(4, Random(it)).sorted()) }
    }
}
