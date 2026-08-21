package tiiehenry.android.app.snapshot.main.launch.addgroup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SingletonViewModelFactory
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.databinding.BottomSheetAddGroupBinding
import tiiehenry.android.app.snapshot.main.launch.LauncherViewModel
import tiiehenry.android.app.snapshot.main.launch.userMessage
import tiiehenry.android.app.snapshot.repository.PathRegistrationResult
import tiiehenry.android.app.snapshot.utils.GroupPathPickerHelper
import tiiehenry.android.snapshot.app.UserInfoHide
import java.nio.file.Paths

class AddGroupBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddGroupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LauncherViewModel by activityViewModels()
    private val snapshotViewModel: SnapshotViewModel by activityViewModels {
        SingletonViewModelFactory(SnapshotApp.getViewModel())
    }

    private lateinit var userIdSpinner: Spinner
    private val userInfoList = mutableListOf<UserInfoHide>()
    private var addingSet = false

    private val pathPickerHelper = GroupPathPickerHelper(this) { absolutePath, uri ->
        binding.etGroupPath.setText(absolutePath)
        GroupPathPickerHelper.takePersistablePermission(this, uri)
        GroupPathPickerHelper.autoFillGroupName(
            this,
            uri,
            absolutePath,
            binding.etGroupName
        )
        if (!addingSet) {
            val configData = GroupPathPickerHelper.readGroupConfigData(this, uri)
            if (configData != null) {
                val idx = userInfoList.indexOfFirst { it.id == configData.userId }
                if (idx >= 0) userIdSpinner.setSelection(idx)
            }
        } else if (binding.etGroupName.text.isNullOrBlank()) {
            val base = Paths.get(absolutePath).fileName?.toString().orEmpty()
            if (base.isNotEmpty()) binding.etGroupName.setText(base)
        }
    }

    override fun getTheme(): Int = R.style.ThemeOverlay_AppSnapshot_BottomSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pathPickerHelper.register()
        addingSet = savedInstanceState?.getBoolean(STATE_ADDING_SET)
            ?: arguments?.getBoolean(ARG_START_AS_SET, false)
            ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_ADDING_SET, addingSet)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnConfirm.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_button_filled_primary)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        }

        arguments?.getString(ARG_SUGGESTED_PATH)?.takeIf { it.isNotBlank() }?.let {
            binding.etGroupPath.setText(it)
        }
        arguments?.getString(ARG_SUGGESTED_NAME)?.takeIf { it.isNotBlank() }?.let {
            if (binding.etGroupName.text.isNullOrBlank()) {
                binding.etGroupName.setText(it)
            }
        }

        binding.toggleAddKind.check(if (addingSet) R.id.btn_kind_set else R.id.btn_kind_group)
        binding.toggleAddKind.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            addingSet = checkedId == R.id.btn_kind_set
            applyKindUi()
        }
        applyKindUi()

        userIdSpinner = binding.spinnerUserId
        lifecycleScope.launch {
            val users = withContext(Dispatchers.IO) {
                try {
                    SnapshotApp.getInstance().appManager.users ?: emptyList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }
            userInfoList.clear()
            userInfoList.addAll(users)
            val userLabels = userInfoList.map { "${it.name} (${it.id})" }.toTypedArray()
            val userAdapter =
                ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, userLabels)
            userAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            userIdSpinner.adapter = userAdapter
        }

        binding.tilGroupPath.setEndIconOnClickListener {
            pathPickerHelper.launch()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            submit()
        }
    }

    private fun applyKindUi() {
        if (addingSet) {
            binding.tvAddTitle.setText(R.string.group_set_add_title)
            binding.tilGroupName.setHint(getString(R.string.group_set_name_hint))
            binding.tilGroupPath.setHint(getString(R.string.group_set_path_hint))
            binding.userSection.visibility = View.GONE
        } else {
            binding.tvAddTitle.setText(R.string.group_add_title)
            binding.tilGroupName.setHint(getString(R.string.group_name_hint))
            binding.tilGroupPath.setHint(getString(R.string.group_path_hint))
            binding.userSection.visibility = View.VISIBLE
        }
    }

    private fun submit() {
        val name = binding.etGroupName.text.toString().trim()
        val path = binding.etGroupPath.text.toString().trim()
        if (name.isEmpty()) {
            binding.etGroupName.error = getString(
                if (addingSet) R.string.error_enter_group_set_name else R.string.error_enter_group_name
            )
        }
        if (path.isEmpty()) {
            binding.etGroupPath.error = getString(
                if (addingSet) R.string.error_select_group_set_path else R.string.error_select_group_path
            )
        }
        if (name.isEmpty() || path.isEmpty()) return

        if (addingSet) {
            val appContext = requireContext().applicationContext
            val setName = name
            snapshotViewModel.addGroupSet(setName, path) { result ->
                val error = result.userMessage(appContext)
                if (error != null) {
                    Toast.makeText(appContext, error, Toast.LENGTH_SHORT).show()
                    return@addGroupSet
                }
                val count = (result as? PathRegistrationResult.Ok)?.discoveredCount ?: 0
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.group_set_added_toast, setName, count),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            val selectedIndex = userIdSpinner.selectedItemPosition
            val userId = if (selectedIndex >= 0 && selectedIndex < userInfoList.size) {
                userInfoList[selectedIndex].id
            } else 0
            val appContext = requireContext().applicationContext
            snapshotViewModel.addGroup(name, path, userId) { result ->
                result.userMessage(appContext)?.let {
                    Toast.makeText(appContext, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddGroupBottomSheet"
        private const val ARG_SUGGESTED_PATH = "suggested_path"
        private const val ARG_SUGGESTED_NAME = "suggested_name"
        private const val ARG_START_AS_SET = "start_as_set"
        private const val STATE_ADDING_SET = "state_adding_set"

        fun newInstance(
            suggestedPath: String? = null,
            suggestedName: String? = null,
            startAsSet: Boolean = false,
        ): AddGroupBottomSheet {
            return AddGroupBottomSheet().apply {
                arguments = Bundle().apply {
                    if (suggestedPath != null) putString(ARG_SUGGESTED_PATH, suggestedPath)
                    if (suggestedName != null) putString(ARG_SUGGESTED_NAME, suggestedName)
                    putBoolean(ARG_START_AS_SET, startAsSet)
                }
            }
        }
    }
}
