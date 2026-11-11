package il.drivetheory.domain

import il.drivetheory.core.model.ExamRules
import il.drivetheory.core.model.Question
import il.drivetheory.core.model.Topic
import kotlin.random.Random

/**
 * Builds an exam the way the official test does: a random selection from the pool of the
 * candidate's licence class. The Ministry does not publish a per-topic quota, so the topics are
 * represented proportionally to their share of that pool (largest-remainder rounding), which keeps
 * every exam balanced instead of occasionally being, say, all road signs.
 */
class ExamGenerator(private val random: Random = Random.Default) {

    fun generate(pool: List<Question>, count: Int = ExamRules.QUESTION_COUNT): List<Question> {
        if (pool.size <= count) return pool.shuffled(random)
        val byTopic: Map<Topic, List<Question>> = pool.groupBy { it.topic }
        val quotas = topicQuotas(byTopic.mapValues { it.value.size }, count)
        return quotas.flatMap { (topic, quota) -> byTopic.getValue(topic).shuffled(random).take(quota) }
            .shuffled(random)
    }

    /** Hare–Niemeyer apportionment of [count] seats between topics by pool size. */
    internal fun topicQuotas(sizes: Map<Topic, Int>, count: Int): Map<Topic, Int> {
        val total = sizes.values.sum()
        val exact = sizes.mapValues { (_, size) -> size.toDouble() * count / total }
        val quotas = exact.mapValues { it.value.toInt() }.toMutableMap()
        var remaining = count - quotas.values.sum()
        exact.entries
            .sortedByDescending { it.value - it.value.toInt() }
            .forEach { (topic, _) ->
                if (remaining > 0 && quotas.getValue(topic) < sizes.getValue(topic)) {
                    quotas[topic] = quotas.getValue(topic) + 1
                    remaining--
                }
            }
        return quotas.filterValues { it > 0 }
    }
}

/** Random order of the four options, so the position of the right answer can't be memorized. */
fun shuffledOptionOrder(optionCount: Int, random: Random = Random.Default): List<Int> =
    (0 until optionCount).shuffled(random)
