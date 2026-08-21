package tiiehenry.android.app.snapshot.main.launch.addgroup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SingletonViewModelFactory
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.databinding.BottomSheetAddGroupSetBinding
import tiiehenry.android.app.snapshot.utils.GroupPathPickerHelper
import java.nio.file.Paths

class AddGroupSetBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddGroupSetBinding? = null
    private val binding get() = _binding!!
    private val snapshotViewModel: SnapshotViewModel by activityViewModels {
        SingletonViewModelFactory(SnapshotApp.getViewModel())
    }

    private val pathPickerHelper = GroupPathPickerHelper(this) { absolutePath, uri ->
        binding.etSetPath.setText(absolutePath)
        GroupPathPickerHelper.takePersistablePermission(this, uri)
        if (binding.etSetName.text.isNullOrBlank()) {
            val base = Paths.get(absolutePath).fileName?.toString().orEmpty()
            if (base.isNotEmpty()) binding.etSetName.setText(base)
        }
    }

    override fun getTheme(): Int = R.style.ThemeOverlay_AppSnapshot_BottomSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pathPickerHelper.register()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetAddGroupSetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnConfirm.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_button_filled_primary)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
        }
        binding.tilSetPath.setEndIconOnClickListener { pathPickerHelper.launch() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnConfirm.setOnClickListener {
            val name = binding.etSetName.text.toString().trim()
            val path = binding.etSetPath.text.toString().trim()
            if (name.isEmpty()) {
                binding.etSetName.error = getString(R.string.error_enter_group_set_name)
                return@setOnClickListener
            }
            if (path.isEmpty()) {
                binding.etSetPath.error = getString(R.string.error_select_group_set_path)
                return@setOnClickListener
            }
            snapshotViewModel.addGroupSet(name, path) { count ->
                Toast.makeText(
                    requireContext(),
                    getString(R.string.group_set_added_toast, name, count),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddGroupSetBottomSheet"
        fun newInstance() = AddGroupSetBottomSheet()
    }
}
