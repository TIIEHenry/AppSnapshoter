package tiiehenry.android.app.snapshot.main.launch.groupset

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SingletonViewModelFactory
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.databinding.FragmentGroupSetSettingBinding
import tiiehenry.android.app.snapshot.group.GroupSetColors
import tiiehenry.android.app.snapshot.main.launch.userMessage
import tiiehenry.android.app.snapshot.repository.AppDataRepository
import tiiehenry.android.app.snapshot.repository.PathRegistrationResult
import tiiehenry.android.app.snapshot.utils.GroupPathPickerHelper

class GroupSetSettingFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentGroupSetSettingBinding? = null
    private val binding get() = _binding!!
    private val snapshotViewModel: SnapshotViewModel by activityViewModels {
        SingletonViewModelFactory(SnapshotApp.getViewModel())
    }
    private lateinit var setId: String
    private var selectedAccent: Int = GroupSetColors.PRESETS[0]

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
        selectedAccent = set.accentColor
        binding.tilSetPath.setEndIconOnClickListener { pathPickerHelper.launch() }
        binding.btnColorCustom.setOnClickListener {
            AccentColorPickerDialog.show(requireContext(), selectedAccent) { picked ->
                selectedAccent = picked
                bindColorRow()
            }
        }
        bindColorRow()

        binding.btnSave.apply {
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_button_filled_primary)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.on_primary))
            setOnClickListener {
                val name = binding.etSetName.text.toString().trim()
                val path = binding.etSetPath.text.toString().trim()
                if (name.isEmpty() || path.isEmpty()) return@setOnClickListener
                snapshotViewModel.updateGroupSetPath(
                    setId = setId,
                    newPath = path,
                    newName = name,
                    accentColor = selectedAccent,
                ) { result ->
                    when (result) {
                        is PathRegistrationResult.Ok -> dismiss()
                        else -> result.userMessage(requireContext())?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        binding.btnDelete.setOnClickListener { showDeleteDialog(set.name) }
    }

    private fun bindColorRow() {
        val row = binding.colorRow
        if (row.width <= 0) {
            row.post { bindColorRow() }
            return
        }
        row.removeAllViews()

        val density = resources.displayMetrics.density
        val size = (28 * density).toInt()
        val gap = (6 * density).toInt()
        val strokeSelected = (2 * density).toInt()
        val onSurface = ContextCompat.getColor(requireContext(), R.color.on_surface)

        updateCustomColorButton()

        val cell = size + gap
        val columns = (row.width / cell).coerceAtLeast(1)
        val sidePad = ((row.width - columns * cell) / 2).coerceAtLeast(0)
        row.setPaddingRelative(sidePad, 0, sidePad, 0)
        row.columnCount = columns

        fun swatchLp(): GridLayout.LayoutParams =
            GridLayout.LayoutParams().apply {
                width = size
                height = size
                setMargins(gap / 2, gap / 2, gap / 2, gap / 2)
            }

        fun addColorSwatch(color: Int) {
            val swatch = View(requireContext()).apply {
                layoutParams = swatchLp()
                background = (ContextCompat.getDrawable(requireContext(), R.drawable.bg_color_swatch)
                    ?.mutate() as GradientDrawable).also { d ->
                    d.setColor(color)
                    d.setStroke(
                        if (GroupSetColors.sameColor(color, selectedAccent)) strokeSelected else 0,
                        onSurface,
                    )
                }
                contentDescription = getString(R.string.group_set_color_label)
                setOnClickListener {
                    selectedAccent = color
                    bindColorRow()
                }
            }
            row.addView(swatch)
        }

        for (color in GroupSetColors.PRESETS) {
            addColorSwatch(color)
        }

        if (!GroupSetColors.isPreset(selectedAccent)) {
            addColorSwatch(selectedAccent)
        }
    }

    private fun updateCustomColorButton() {
        val density = resources.displayMetrics.density
        val stroke = if (!GroupSetColors.isPreset(selectedAccent)) {
            (2 * density).toInt()
        } else {
            0
        }
        val fill = if (GroupSetColors.isPreset(selectedAccent)) {
            ContextCompat.getColor(requireContext(), R.color.fluent_fill_subtle)
        } else {
            selectedAccent
        }
        binding.btnColorCustom.background =
            (ContextCompat.getDrawable(requireContext(), R.drawable.bg_color_swatch)
                ?.mutate() as GradientDrawable).also { d ->
                d.setColor(fill)
                d.setStroke(
                    stroke,
                    ContextCompat.getColor(requireContext(), R.color.on_surface),
                )
            }
        val iconTint = if (!GroupSetColors.isPreset(selectedAccent) &&
            android.graphics.Color.luminance(selectedAccent) < 0.45
        ) {
            ContextCompat.getColor(requireContext(), R.color.white)
        } else {
            ContextCompat.getColor(requireContext(), R.color.icon_primary)
        }
        ImageViewCompat.setImageTintList(
            binding.btnColorCustom,
            android.content.res.ColorStateList.valueOf(iconTint),
        )
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
