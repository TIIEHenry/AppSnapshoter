package tiiehenry.android.app.snapshot.ui.widget

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.LayoutSearchFieldBinding

class CollapsibleSearchController(
    private val toggle: MaterialButton,
    private val searchField: LayoutSearchFieldBinding,
    private val transitionHost: ViewGroup,
    private val onQueryChanged: (String) -> Unit,
    hint: String,
    initialQuery: String = ""
) {
    var expanded = false
        private set

    init {
        searchField.searchInput.hint = hint
        searchField.searchInputLayout.setupSearchQueryListener { query ->
            onQueryChanged(query)
            updateToggleState()
        }

        toggle.setOnClickListener {
            if (expanded) {
                collapse()
            } else {
                expand()
            }
        }

        if (initialQuery.isNotBlank()) {
            searchField.searchInput.setText(initialQuery)
            expand(showKeyboard = false)
        } else {
            updateToggleState()
        }
    }

    fun expand(showKeyboard: Boolean = true) {
        if (expanded) return
        expanded = true
        animateTransition {
            searchField.root.visibility = View.VISIBLE
            toggle.setIconResource(R.drawable.ic_close)
            toggle.contentDescription = toggle.context.getString(R.string.timeline_search_close)
        }
        updateToggleState()
        if (showKeyboard) {
            showIme(searchField.searchInput)
        }
    }

    fun collapse() {
        if (!expanded) return
        expanded = false
        animateTransition {
            searchField.root.visibility = View.GONE
            toggle.setIconResource(R.drawable.ic_search)
            toggle.contentDescription = toggle.context.getString(R.string.timeline_search_toggle)
        }
        hideIme(searchField.searchInput)
        searchField.searchInput.clearFocus()
        updateToggleState()
    }

    fun currentQuery(): String = searchField.searchInputLayout.searchQuery()

    private fun updateToggleState() {
        val hasQuery = currentQuery().isNotBlank()
        val tintRes = if (hasQuery && !expanded) R.color.primary else R.color.icon_secondary
        toggle.iconTint = ColorStateList.valueOf(ContextCompat.getColor(toggle.context, tintRes))
    }

    private fun animateTransition(block: () -> Unit) {
        TransitionManager.beginDelayedTransition(
            transitionHost,
            AutoTransition().apply { duration = SEARCH_TRANSITION_MS }
        )
        block()
    }

    private fun showIme(view: View) {
        view.requestFocus()
        view.post {
            view.context.getSystemService<InputMethodManager>()
                ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideIme(view: View) {
        view.context.getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    companion object {
        private const val SEARCH_TRANSITION_MS = 180L
    }
}
