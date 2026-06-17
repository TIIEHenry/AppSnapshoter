package tiiehenry.android.app.snapshot.main.timeline

import android.content.Context
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import com.google.android.material.color.MaterialColors

object TimelineTextHighlight {

  fun highlight(context: Context, text: String, query: String): SpannableString {
    val spannable = SpannableString(text)
    if (query.isBlank()) return spannable

    val highlightColor = MaterialColors.getColor(
      context,
      com.google.android.material.R.attr.colorPrimaryContainer,
      0
    )
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    var start = 0
    while (true) {
      val index = lowerText.indexOf(lowerQuery, start)
      if (index < 0) break
      spannable.setSpan(
        BackgroundColorSpan(highlightColor),
        index,
        index + query.length,
        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
      )
      start = index + query.length
    }
    return spannable
  }
}
