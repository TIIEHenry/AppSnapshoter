package tiiehenry.android.app.snapshot.ui.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextHighlightTest {

    @Test
    fun `blank query yields no ranges`() {
        assertTrue(TextHighlight.matchRanges("WeChat", "").isEmpty())
        assertTrue(TextHighlight.matchRanges("WeChat", "   ").isEmpty())
    }

    @Test
    fun `match is case insensitive and can repeat`() {
        assertEquals(listOf(0 until 6), TextHighlight.matchRanges("WeChat", "wechat"))
        assertEquals(
            listOf(0 until 2, 4 until 6),
            TextHighlight.matchRanges("abXXab", "AB"),
        )
    }

    @Test
    fun `no match yields empty`() {
        assertTrue(TextHighlight.matchRanges("Work", "play").isEmpty())
    }
}
