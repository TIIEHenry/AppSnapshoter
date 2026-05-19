package tiiehenry.android.app.snapshot.main.launch.groupsort

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshot.SingletonViewModelFactory
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.config.GlobalConfig
import tiiehenry.android.app.snapshot.databinding.BottomSheetGroupSortBinding
import tiiehenry.android.app.snapshot.group.SnapGroup
import java.util.Collections

class GroupSortBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetGroupSortBinding? = null
    private val binding get() = _binding!!
    private val snapshotViewModel: SnapshotViewModel by activityViewModels {
        SingletonViewModelFactory(SnapshotApp.getViewModel())
    }
    private lateinit var adapter: GroupSortAdapter
    private val groups = mutableListOf<SnapGroup>()
    private var onSortSavedListener: (() -> Unit)? = null

    fun setOnSortSavedListener(listener: () -> Unit) {
        onSortSavedListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetGroupSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadGroups()

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            saveSortOrder()
            onSortSavedListener?.invoke()
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        binding.rvGroups.layoutManager = LinearLayoutManager(requireContext())
        adapter = GroupSortAdapter { fromPosition, toPosition ->
            // 交换数据
            Collections.swap(groups, fromPosition, toPosition)
            adapter.notifyItemMoved(fromPosition, toPosition)
        }
        binding.rvGroups.adapter = adapter

        // 设置拖拽帮助器
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                if (fromPosition != RecyclerView.NO_POSITION && toPosition != RecyclerView.NO_POSITION) {
                    adapter.onItemMove(fromPosition, toPosition)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不处理滑动
            }

            override fun isLongPressDragEnabled(): Boolean {
                return true
            }

            override fun isItemViewSwipeEnabled(): Boolean {
                return false
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvGroups)
    }

    private fun loadGroups() {
        val currentGroups = snapshotViewModel.groupList.value
        if (currentGroups != null) {
            groups.clear()
            groups.addAll(currentGroups)
            adapter.submitList(groups.toList())
        }
    }

    private fun saveSortOrder() {
        // 获取排序后的分组ID列表（保持List顺序）
        val sortedGroupIds = groups.map { it.id }

        // 保存到全局配置
        GlobalConfig.groups = sortedGroupIds

        // 重新加载分组列表以应用新顺序
        snapshotViewModel.loadGroups()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "GroupSortBottomSheet"

        fun newInstance(): GroupSortBottomSheet {
            return GroupSortBottomSheet()
        }
    }
}