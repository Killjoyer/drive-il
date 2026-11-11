package il.drivetheory.data

import il.drivetheory.data.content.SignTextsDto
import il.drivetheory.data.content.withFallback
import il.drivetheory.domain.question
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentFallbackTest {

    @Test
    fun `questions missing in the selected language come from the fallback`() {
        val own = listOf(question(1).copy(text = "own 1"), question(3).copy(text = "own 3"))
        val fallback = listOf(question(1), question(2), question(3), question(4))
        val merged = own.withFallback(fallback)
        assertEquals(listOf(1, 2, 3, 4), merged.map { it.id })
        assertEquals(listOf("own 1", "Q2", "own 3", "Q4"), merged.map { it.text })
    }

    @Test
    fun `questions only in the selected language are kept`() {
        assertEquals(listOf(1, 5), listOf(question(5)).withFallback(listOf(question(1))).map { it.id })
    }

    @Test
    fun `sign texts prefer the selected language`() {
        val own = SignTextsDto(mapOf("A" to "own A"), mapOf("1" to "own 1"))
        val fallback = SignTextsDto(mapOf("A" to "en A", "B" to "en B"), mapOf("1" to "en 1", "2" to "en 2"))
        val merged = own.withFallback(fallback)
        assertEquals(mapOf("A" to "own A", "B" to "en B"), merged.categories)
        assertEquals(mapOf("1" to "own 1", "2" to "en 2"), merged.meanings)
    }
}
