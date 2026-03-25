package tiiehenry.android.app.snapshot.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import tiiehenry.android.app.snapshot.databinding.DialogLoadingBinding

/**
 * 简单的Loading对话框
 */
class LoadingDialog(
    context: Context
) : AbstractLoadingDialog(
    context,
    DialogLoadingBinding.inflate(LayoutInflater.from(context))
), ILoadingDialog {

}