package tiiehenry.android.app.snapshot.ui.widget

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.widget.ImageViewCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.LayoutSearchFieldBinding

class CollapsibleSearchController(
    toggle: ImageView,
    private val searchField: LayoutSearchFieldBinding,
    private val transitionHost: ViewGroup,
    private val onQueryChanged: (String) -> Unit,
    hint: String,
    initialQuery: String = ""
) {
    var expanded = false
        private set

    private var toggle: ImageView = toggle

    private val toggleClickListener = View.OnClickListener {
        if (expanded) {
            collapse()
        } else {
            expand()
        }
    }

    init {
        searchField.searchInput.hint = hint
        searchField.searchInputLayout.setupSearchQueryListener { query ->
            onQueryChanged(query)
            updateToggleState()
        }

        attachToggleClick()

        if (initialQuery.isNotBlank()) {
            searchField.searchInput.setText(initialQuery)
            expand(showKeyboard = false)
        } else {
            syncToggleAppearance()
        }
    }

    /**
     * Menu 重建时只换开关图标。不重绑输入监听，非空 query 也不强迫 [expand]。
     */
    fun rebindToggle(newToggle: ImageView) {
        if (toggle !== newToggle) {
            toggle.setOnClickListener(null)
            toggle = newToggle
        }
        attachToggleClick()
        syncToggleAppearance()
    }

    fun expand(showKeyboard: Boolean = true) {
        if (expanded) return
        expanded = true
        animateTransition {
            searchField.root.visibility = View.VISIBLE
            applyToggleIcons()
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
            applyToggleIcons()
        }
        hideIme(searchField.searchInput)
        searchField.searchInput.clearFocus()
        updateToggleState()
    }

    fun currentQuery(): String = searchField.searchInputLayout.searchQuery()

    private fun attachToggleClick() {
        toggle.setOnClickListener(toggleClickListener)
    }

    private fun syncToggleAppearance() {
        applyToggleIcons()
        updateToggleState()
    }

    private fun applyToggleIcons() {
        if (expanded) {
            toggle.setImageResource(R.drawable.ic_close)
            toggle.contentDescription = toggle.context.getString(R.string.timeline_search_close)
        } else {
            toggle.setImageResource(R.drawable.ic_search)
            toggle.contentDescription = toggle.context.getString(R.string.timeline_search_toggle)
        }
    }

    private fun updateToggleState() {
        val hasQuery = currentQuery().isNotBlank()
        val tintRes = if (hasQuery && !expanded) R.color.primary else R.color.icon_secondary
        ImageViewCompat.setImageTintList(
            toggle,
            ColorStateList.valueOf(ContextCompat.getColor(toggle.context, tintRes))
        )
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
