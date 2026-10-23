package tiiehenry.android.app.snapshot.external

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.FragmentExternalAccessBinding

class ExternalAccessFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentExternalAccessBinding? = null
    private val binding get() = _binding!!
    private val access by lazy { ExternalAccessStore(requireContext()) }

    override fun getTheme(): Int = R.style.ThemeOverlay_AppSnapshot_BottomSheet

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExternalAccessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        render()
        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            access.update { it.copy(enabled = checked) }
            ExternalRuntime.refresh(requireContext())
        }
        binding.targetMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.mode_all) TargetMode.ALL else TargetMode.WHITELIST
            access.update { it.copy(targetMode = mode) }
        }
        binding.whitelist.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveWhitelist()
        }
        binding.btnApprove.setOnClickListener {
            access.update { state ->
                state.copy(approved = state.approved + state.pending, pending = emptySet())
            }
            render()
        }
        binding.btnRevoke.setOnClickListener {
            access.update { it.copy(approved = emptySet(), pending = emptySet(), mcpEnabled = false, mcpTokenHash = "") }
            ExternalRuntime.refresh(requireContext())
            render()
        }
        binding.switchMcp.setOnCheckedChangeListener { _, checked ->
            access.update { state ->
                if (!checked) state.copy(mcpEnabled = false)
                else {
                    val token = ExternalRuntime.newToken()
                    binding.mcpToken.text = getString(R.string.external_mcp_token, token)
                    state.copy(
                        mcpEnabled = true,
                        mcpTokenHash = McpInstanceServer.sha256(token),
                        approved = state.approved + ExternalAccess.mcpCaller,
                    )
                }
            }
            ExternalRuntime.refresh(requireContext())
        }
    }

    private fun saveWhitelist() {
        val packages = binding.whitelist.text?.toString().orEmpty()
            .split('\n', ' ', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        access.update { it.copy(targetPackages = packages) }
    }

    private fun render() {
        val state = access.read()
        binding.switchEnabled.setOnCheckedChangeListener(null)
        binding.switchMcp.setOnCheckedChangeListener(null)
        binding.switchEnabled.isChecked = state.enabled
        binding.switchMcp.isChecked = state.mcpEnabled
        binding.modeAll.isChecked = state.targetMode == TargetMode.ALL
        binding.modeWhitelist.isChecked = state.targetMode == TargetMode.WHITELIST
        if (!binding.whitelist.hasFocus()) {
            binding.whitelist.setText(state.targetPackages.joinToString("\n"))
        }
        val approved = state.approved.joinToString("\n") { "${it.packageName} ${it.certSha256.take(12)}" }
        val pending = state.pending.joinToString("\n") { "${it.packageName} ${it.certSha256.take(12)}" }
        binding.callers.text = getString(R.string.external_callers, approved, pending)
        binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
            access.update { it.copy(enabled = checked) }
            ExternalRuntime.refresh(requireContext())
        }
        binding.switchMcp.setOnCheckedChangeListener { _, checked ->
            access.update { stateNow ->
                if (!checked) {
                    binding.mcpToken.text = ""
                    stateNow.copy(mcpEnabled = false)
                } else {
                    val token = ExternalRuntime.newToken()
                    binding.mcpToken.text = getString(R.string.external_mcp_token, token)
                    stateNow.copy(
                        mcpEnabled = true,
                        mcpTokenHash = McpInstanceServer.sha256(token),
                        approved = stateNow.approved + ExternalAccess.mcpCaller,
                    )
                }
            }
            ExternalRuntime.refresh(requireContext())
        }
    }

    override fun onDestroyView() {
        saveWhitelist()
        _binding = null
        super.onDestroyView()
    }
}
