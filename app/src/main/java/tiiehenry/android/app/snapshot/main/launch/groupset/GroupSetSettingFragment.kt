package tiiehenry.android.app.snapshot.main.launch.groupset

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SingletonViewModelFactory
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.databinding.FragmentGroupSetSettingBinding
import tiiehenry.android.app.snapshot.repository.AppDataRepository
import tiiehenry.android.app.snapshot.utils.GroupPathPickerHelper

class GroupSetSettingFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentGroupSetSettingBinding? = null
    private val binding get() = _binding!!
    private val snapshotViewModel: SnapshotViewModel by activityViewModels {
        SingletonViewModelFactory(SnapshotApp.getViewModel())
    }
    private lateinit var setId: String

    private val pathPickerHelper = GroupPathPickerHelper(this) { absolutePath, uri ->
        binding.etSetPath.setText(absolutePath)
        GroupPathPickerHelper.takePersistablePermission(this, uri)
    }

    override fun getTheme(): Int = R.style.ThemeOverlay_AppSnapshot_BottomSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pathPickerHelper.register()
        setId = requireArguments().getString(ARG_SET_ID)
            ?: throw IllegalArgumentException("setId required")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentGroupSetSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val set = snapshotViewModel.resolveGroupSet(setId) ?: run {
            dismiss()
            return
        }
        binding.etSetName.setText(set.name)
        binding.etSetPath.setText(set.path)
        binding.tilSetPath.setEndIconOnClickListener { pathPickerHelper.launch() }

        binding.btnSave.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_button_filled_primary)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
            setOnClickListener {
                val name = binding.etSetName.text.toString().trim()
                val path = binding.etSetPath.text.toString().trim()
                if (name.isEmpty() || path.isEmpty()) return@setOnClickListener
                snapshotViewModel.updateGroupSetPath(setId, path, name)
                dismiss()
            }
        }
        binding.btnDelete.setOnClickListener { showDeleteDialog(set.name) }
    }

    private fun showDeleteDialog(setName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.group_set_delete_title)
            .setMessage(getString(R.string.group_set_delete_message, setName))
            .setPositiveButton(R.string.group_set_delete_set_only) { _, _ ->
                snapshotViewModel.deleteGroupSet(
                    setId,
                    AppDataRepository.DeleteGroupSetMode.SET_ONLY,
                )
                dismiss()
            }
            .setNeutralButton(R.string.group_set_delete_set_and_groups) { _, _ ->
                snapshotViewModel.deleteGroupSet(
                    setId,
                    AppDataRepository.DeleteGroupSetMode.SET_AND_GROUPS,
                )
                dismiss()
            }
            .setNegativeButton(R.string.group_set_delete_with_files) { _, _ ->
                snapshotViewModel.deleteGroupSet(
                    setId,
                    AppDataRepository.DeleteGroupSetMode.DELETE_FILES,
                )
                dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "GroupSetSettingFragment"
        private const val ARG_SET_ID = "set_id"

        fun newInstance(setId: String): GroupSetSettingFragment {
            return GroupSetSettingFragment().apply {
                arguments = Bundle().apply { putString(ARG_SET_ID, setId) }
            }
        }
    }
}
