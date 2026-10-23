package tiiehenry.android.app.snapshot.instance

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SingletonViewModelFactory
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.databinding.BottomSheetInstancesBinding
import tiiehenry.android.app.snapshot.databinding.DialogInstanceFormBinding
import tiiehenry.android.app.snapshot.databinding.ItemInstanceBinding
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.ItemProgressDialog
import java.text.DateFormat
import java.util.Date

class InstanceSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetInstancesBinding? = null
    private val binding get() = _binding!!
    private val snapshotViewModel: SnapshotViewModel by activityViewModels {
        SingletonViewModelFactory(SnapshotApp.getViewModel())
    }
    private val rows = InstanceRowAdapter(
        onClick = { instance -> showActions(instance) },
    )
    private var running = false

    override fun getTheme(): Int = R.style.ThemeOverlay_AppSnapshot_BottomSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetInstancesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvTitle.text = titleOf(findApp())
        binding.instanceList.layoutManager = LinearLayoutManager(requireContext())
        binding.instanceList.adapter = rows
        binding.btnCreate.setOnClickListener { showForm(null) }
        reload()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun reload() {
        val app = findApp() ?: return
        val packageDir = app.packageDir
        viewLifecycleOwner.lifecycleScope.launch {
            val instances = withContext(Dispatchers.IO) {
                InstanceCatalog(SnapshotApp.getInstance().fileSystem, packageDir).list()
            }
            if (_binding == null) return@launch
            rows.submit(instances)
            binding.tvEmpty.visibility = if (instances.isEmpty()) View.VISIBLE else View.GONE
            binding.btnCreate.isEnabled = !running
        }
    }

    private fun showActions(instance: AppInstance) {
        if (running) return
        val labels = arrayOf(
            getString(R.string.instance_restore),
            getString(R.string.instance_update),
            getString(R.string.instance_edit),
            getString(R.string.instance_delete),
        )
        AlertDialog.Builder(requireContext())
            .setTitle(instance.name)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> confirm(
                        getString(R.string.instance_restore_title, instance.name),
                        getString(R.string.instance_restore_message),
                    ) { runOp { restore(instance.id) } }
                    1 -> confirm(
                        getString(R.string.instance_update_title, instance.name),
                        getString(R.string.instance_update_message),
                    ) { runOp { update(instance.id) } }
                    2 -> showForm(instance)
                    else -> confirm(
                        getString(R.string.instance_delete_title, instance.name),
                        getString(R.string.instance_delete_message),
                    ) { runOp { delete(instance.id) } }
                }
            }
            .show()
    }

    private fun showForm(existing: AppInstance?) {
        if (running) return
        val form = DialogInstanceFormBinding.inflate(layoutInflater)
        if (existing != null) {
            form.etName.setText(existing.name)
            form.etAccount.setText(existing.account)
            form.etNote.setText(existing.note)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(if (existing == null) R.string.instance_create else R.string.instance_edit)
            .setView(form.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = form.etName.text?.toString().orEmpty()
                val account = form.etAccount.text?.toString().orEmpty()
                val note = form.etNote.text?.toString().orEmpty()
                if (existing == null) {
                    runOp { create(name, account, note) }
                } else {
                    runOp { edit(existing.id, name, account, note) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirm(title: String, message: String, onOk: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runOp(block: suspend InstanceStore.() -> String) {
        if (running) return
        val app = findApp()
        if (app == null) {
            toast(getString(R.string.apps_popup_group_gone))
            return
        }
        running = true
        isCancelable = false
        binding.btnCreate.isEnabled = false
        val progress = ItemProgressDialog(requireContext())
        progress.setItemMessage(getString(R.string.instance_progress_save))
        progress.setItemStatus(getString(R.string.ellipsis))
        progress.showItem()
        val host = requireActivity()
        host.lifecycleScope.launch {
            try {
                val message = InstanceStore(host, app, progress).block()
                if (isAdded) toast(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isAdded) toast(e.message ?: getString(R.string.error_unknown))
            } finally {
                progress.dismissItem()
                running = false
                if (isAdded) isCancelable = true
                if (_binding != null) reload()
            }
        }
    }

    private fun titleOf(app: ArchivedApp?): String {
        if (app == null) return getString(R.string.instance_title)
        return try {
            app.appInfo.archiveLabel ?: app.appInfo.packageName
        } catch (e: UninitializedPropertyAccessException) {
            getString(R.string.instance_title)
        }
    }

    private fun findApp(): ArchivedApp? {
        val groupId = arguments?.getString(ARG_GROUP_ID) ?: return null
        val packageDir = arguments?.getString(ARG_PACKAGE_DIR) ?: return null
        val group = snapshotViewModel.resolveGroup(groupId) ?: return null
        return group.apps.firstOrNull { it.packageDir == packageDir }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val ARG_GROUP_ID = "groupId"
        private const val ARG_PACKAGE_DIR = "packageDir"

        fun show(fragmentManager: FragmentManager, groupId: String, packageDir: String) {
            InstanceSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_GROUP_ID, groupId)
                    putString(ARG_PACKAGE_DIR, packageDir)
                }
            }.show(fragmentManager, "instances")
        }
    }
}

private class InstanceRowAdapter(
    private val onClick: (AppInstance) -> Unit,
) : RecyclerView.Adapter<InstanceRowAdapter.Holder>() {

    private var items: List<AppInstance> = emptyList()
    private val timeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    fun submit(items: List<AppInstance>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemInstanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val instance = items[position]
        holder.binding.instanceName.text = instance.name
        val whenText = timeFormat.format(Date(instance.updatedAt))
        holder.binding.instanceDetail.text = if (instance.account.isEmpty()) {
            whenText
        } else {
            "${instance.account} · $whenText"
        }
        holder.binding.root.setOnClickListener { onClick(instance) }
    }

    class Holder(val binding: ItemInstanceBinding) : RecyclerView.ViewHolder(binding.root)
}
