package tiiehenry.android.app.snapshot.main.launch.groupsort

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SingletonViewModelFactory
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.config.GlobalConfig
import tiiehenry.android.app.snapshot.databinding.BottomSheetGroupSortBinding
import tiiehenry.android.app.snapshot.group.ArchiveRoot
import tiiehenry.android.app.snapshot.repository.ArchiveListProjector
import tiiehenry.android.app.snapshot.repository.GroupSetMembership
import java.util.Collections

/**
 * 两级排序：顶层只重排 [archiveRoots]；展开集后只重排该集 basename [groupOrder]。
 * 禁止写 [GlobalConfig.groups] 为可见 ID。
 */
class GroupSortBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetGroupSortBinding? = null
    private val binding get() = _binding!!
    private val snapshotViewModel: SnapshotViewModel by activityViewModels {
        SingletonViewModelFactory(SnapshotApp.getViewModel())
    }
    private lateinit var adapter: GroupSortAdapter
    private val items = mutableListOf<SortableItem>()
    /** 集内顺序暂存：展开后拖动写入，折叠后仍保留直至保存 */
    private val pendingMemberOrders = mutableMapOf<String, List<String>>()
    private var onSortSavedListener: (() -> Unit)? = null

    fun setOnSortSavedListener(listener: () -> Unit) {
        onSortSavedListener = listener
    }

    override fun getTheme(): Int = R.style.ThemeOverlay_AppSnapshot_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetGroupSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSave.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_button_filled_primary)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        }

        setupRecyclerView()
        loadItems()

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener {
            saveSortOrder()
            onSortSavedListener?.invoke()
            dismiss()
        }
    }

    private fun setupRecyclerView() {
        binding.rvGroups.layoutManager = LinearLayoutManager(requireContext())
        adapter = GroupSortAdapter(
            onItemMove = { from, to ->
                Collections.swap(items, from, to)
                adapter.notifyItemMoved(from, to)
                captureMemberOrderAfterMove(from, to)
            },
            onToggleExpand = { position -> toggleExpand(position) },
        )
        binding.rvGroups.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                if (!canSwap(from, to)) return false
                adapter.onItemMove(from, to)
                return true
            }

            override fun canDropOver(
                recyclerView: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = current.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                return canSwap(from, to)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = true
            override fun isItemViewSwipeEnabled(): Boolean = false
        })
        itemTouchHelper.attachToRecyclerView(binding.rvGroups)
    }

    private fun captureMemberOrderAfterMove(from: Int, to: Int) {
        val moved = items.getOrNull(to) as? SortableItem.Member
            ?: items.getOrNull(from) as? SortableItem.Member
            ?: return
        pendingMemberOrders[moved.setId] = items.mapNotNull { row ->
            (row as? SortableItem.Member)?.takeIf { it.setId == moved.setId }?.basename
        }
    }

    private fun canSwap(from: Int, to: Int): Boolean {
        val a = items.getOrNull(from) ?: return false
        val b = items.getOrNull(to) ?: return false
        return when {
            a is SortableItem.Root && b is SortableItem.Root -> true
            a is SortableItem.Member && b is SortableItem.Member && a.setId == b.setId -> true
            else -> false
        }
    }

    private fun loadItems() {
        val groupsById = snapshotViewModel.groupList.value.orEmpty().associateBy { it.id }
        val setsById = snapshotViewModel.groupSetList.value.orEmpty().associateBy { it.id }
        items.clear()
        for (root in GlobalConfig.archiveRoots) {
            when (root) {
                is ArchiveRoot.Set -> {
                    val set = setsById[root.setId]
                    items += SortableItem.Root(
                        root = root,
                        label = getString(R.string.group_sort_set_label, set?.name ?: root.setId),
                        expandable = true,
                        expanded = false,
                    )
                }
                is ArchiveRoot.Group -> {
                    items += SortableItem.Root(
                        root = root,
                        label = groupsById[root.groupId]?.name ?: root.groupId,
                        expandable = false,
                        expanded = false,
                    )
                }
            }
        }
        adapter.submitList(items.toList())
    }

    private fun toggleExpand(position: Int) {
        val item = items.getOrNull(position) as? SortableItem.Root ?: return
        if (!item.expandable || item.root !is ArchiveRoot.Set) return
        val setId = item.root.setId
        if (item.expanded) {
            pendingMemberOrders[setId] = items.mapNotNull { row ->
                (row as? SortableItem.Member)?.takeIf { it.setId == setId }?.basename
            }
            items.removeAll { it is SortableItem.Member && it.setId == setId }
            items[position] = item.copy(expanded = false)
        } else {
            val set = snapshotViewModel.resolveGroupSet(setId) ?: return
            val members = snapshotViewModel.groupList.value.orEmpty().filter {
                GroupSetMembership.isMemberOf(it.path, set.path)
            }
            val ordered = ArchiveListProjector.orderGroups(
                pendingMemberOrders[setId] ?: set.groupOrder,
                members.map { ArchiveListProjector.GroupSnap(it.id, it.path) },
            )
            val memberRows = ordered.map { snap ->
                val group = members.first { it.id == snap.id }
                SortableItem.Member(
                    setId = setId,
                    basename = GroupSetMembership.basename(group.path),
                    label = group.name,
                )
            }
            items[position] = item.copy(expanded = true)
            items.addAll(position + 1, memberRows)
        }
        adapter.submitList(items.toList())
    }

    private fun saveSortOrder() {
        // Capture currently expanded member orders
        for (setId in items.mapNotNull { (it as? SortableItem.Member)?.setId }.toSet()) {
            pendingMemberOrders[setId] = items.mapNotNull { row ->
                (row as? SortableItem.Member)?.takeIf { it.setId == setId }?.basename
            }
        }
        val roots = items.mapNotNull { (it as? SortableItem.Root)?.root }
        val memberOrders = pendingMemberOrders.toMap()
        snapshotViewModel.saveArchiveRootsOrder(roots) {
            for ((setId, basenames) in memberOrders) {
                if (basenames.isNotEmpty()) {
                    snapshotViewModel.saveGroupSetOrder(setId, basenames)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "GroupSortBottomSheet"
        fun newInstance(): GroupSortBottomSheet = GroupSortBottomSheet()
    }
}

sealed class SortableItem {
    data class Root(
        val root: ArchiveRoot,
        val label: String,
        val expandable: Boolean,
        val expanded: Boolean,
    ) : SortableItem()

    data class Member(
        val setId: String,
        val basename: String,
        val label: String,
    ) : SortableItem()
}
