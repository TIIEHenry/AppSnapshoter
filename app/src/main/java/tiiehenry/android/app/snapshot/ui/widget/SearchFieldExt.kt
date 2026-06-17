package tiiehenry.android.app.snapshot.ui.widget

import androidx.core.widget.doOnTextChanged
import com.google.android.material.textfield.TextInputLayout

fun TextInputLayout.setupSearchQueryListener(onQueryChanged: (String) -> Unit) {
    editText?.doOnTextChanged { text, _, _, _ ->
        onQueryChanged(text?.toString().orEmpty())
    }
}

fun TextInputLayout.searchQuery(): String =
    editText?.text?.toString().orEmpty()
