package tiiehenry.android.app.snapshot.ui.widget

import android.content.Context
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import com.google.android.material.color.MaterialColors

object TextHighlight {

    fun matchRanges(text: String, query: String): List<IntRange> {
        if (query.isBlank()) return emptyList()
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        val ranges = ArrayList<IntRange>()
        var start = 0
        while (true) {
            val index = lowerText.indexOf(lowerQuery, start)
            if (index < 0) break
            val end = index + query.length
            ranges.add(index until end)
            start = end
        }
        return ranges
    }

    fun highlight(context: Context, text: String, query: String): SpannableString {
        val spannable = SpannableString(text)
        val ranges = matchRanges(text, query)
        if (ranges.isEmpty()) return spannable

        val highlightColor = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorPrimaryContainer,
            0
        )
        for (range in ranges) {
            spannable.setSpan(
                BackgroundColorSpan(highlightColor),
                range.first,
                range.last + 1,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        return spannable
    }
}
