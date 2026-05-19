package tiiehenry.android.app.snapshot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * ViewModel 工厂 - 返回 SnapshotApp 中创建的 SingletonViewModel 实例
 * 用于兼容 activityViewModels() / viewModels() 的标准创建方式
 */
class SingletonViewModelFactory(
    private val viewModel: ViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(viewModel.javaClass)) {
            throw IllegalArgumentException(
                "Expected ${modelClass.name} but factory holds ${viewModel.javaClass.name}"
            )
        }
        return viewModel as T
    }
}
