package tiiehenry.android.app.snapshot.main.launch.makearchive.progress

import android.content.Context
import android.view.LayoutInflater
import tiiehenry.android.app.snapshot.databinding.DialogLoadingBinding

/**
 * 简单的Loading对话框
 */
class ItemProgressDialog(
    context: Context
) : AbstractProgressDialog(
    context,
    DialogLoadingBinding.inflate(LayoutInflater.from(context))
), IItemProgressDialog {

}