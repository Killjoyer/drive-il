package il.drivetheory.domain

import il.drivetheory.core.model.Question
import il.drivetheory.core.model.QuestionStat
import kotlin.random.Random

/**
 * Picks the next practice question.
 *
 * * Questions never answered correctly ("unlearned") are preferred; among them, the ones that were
 *   already answered wrong get a higher weight so mistakes come back sooner.
 * * With probability [reviewProbability] (or always, once everything is learned) an already learned
 *   question is repeated instead; shaky ones (more mistakes relative to correct answers) and ones
 *   not seen for a long time are favoured.
 * * The last few questions are not repeated immediately.
 */
class PracticeSelector(
    private val random: Random = Random.Default,
    private val reviewProbability: Double = DEFAULT_REVIEW_PROBABILITY,
) {

    fun next(
        pool: List<Question>,
        stats: Map<Int, QuestionStat>,
        recentIds: List<Int> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): Question? {
        if (pool.isEmpty()) return null
        val avoid = recentIds.takeLast(minOf(RECENT_WINDOW, pool.size - 1)).toSet()
        val candidates = pool.filter { it.id !in avoid }.ifEmpty { pool }

        val (learned, unlearned) = candidates.partition { stats[it.id]?.isLearned == true }
        val pickLearned = when {
            unlearned.isEmpty() -> true
            learned.isEmpty() -> false
            else -> random.nextDouble() < reviewProbability
        }
        return if (pickLearned) {
            weightedPick(learned) { reviewWeight(stats.getValue(it.id), now) }
        } else {
            weightedPick(unlearned) { q -> 1.0 + minOf(stats[q.id]?.wrongCount ?: 0, 3) }
        }
    }

    private fun reviewWeight(stat: QuestionStat, now: Long): Double {
        val shakiness = (1.0 + stat.wrongCount) / stat.correctCount
        val daysSinceSeen = ((now - stat.lastAnsweredAt).coerceAtLeast(0) / DAY_MS).toDouble()
        return shakiness * (1.0 + minOf(daysSinceSeen, 14.0) / 7.0)
    }

    private inline fun weightedPick(items: List<Question>, weight: (Question) -> Double): Question {
        val weights = items.map(weight)
        var r = random.nextDouble() * weights.sum()
        items.forEachIndexed { i, q ->
            r -= weights[i]
            if (r <= 0) return q
        }
        return items.last()
    }

    companion object {
        const val DEFAULT_REVIEW_PROBABILITY = 0.2
        const val RECENT_WINDOW = 10
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

data class LearningProgress(val learned: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else learned.toFloat() / total
    val percent: Int get() = (fraction * 100).toInt()

    companion object {
        fun of(pool: List<Question>, stats: Map<Int, QuestionStat>) =
            LearningProgress(pool.count { stats[it.id]?.isLearned == true }, pool.size)
    }
}
