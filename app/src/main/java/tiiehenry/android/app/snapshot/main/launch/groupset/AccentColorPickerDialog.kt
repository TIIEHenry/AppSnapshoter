package tiiehenry.android.app.snapshot.main.launch.groupset

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.DialogAccentColorPickerBinding
import tiiehenry.android.app.snapshot.group.GroupSetColors

/**
 * HSV + 十六进制自定义强调色。
 */
object AccentColorPickerDialog {

    fun show(context: Context, initialColor: Int, onPicked: (Int) -> Unit) {
        val binding = DialogAccentColorPickerBinding.inflate(LayoutInflater.from(context))
        val hsv = FloatArray(3)
        Color.colorToHSV(ColorUtils.setAlphaComponent(initialColor, 0xFF), hsv)

        var updatingFromSliders = false
        var updatingFromHex = false

        fun currentColor(): Int = Color.HSVToColor(hsv)

        fun applyPreview() {
            val color = currentColor()
            val bg = (ContextCompat.getDrawable(context, R.drawable.bg_color_swatch)
                ?.mutate() as GradientDrawable)
            bg.setColor(color)
            binding.colorPreview.background = bg
            if (!updatingFromHex) {
                updatingFromSliders = true
                binding.etHex.setText(GroupSetColors.toRgbHex(color))
                updatingFromSliders = false
            }
        }

        fun applySlidersFromHsv() {
            binding.seekHue.progress = hsv[0].toInt().coerceIn(0, 360)
            binding.seekSaturation.progress = (hsv[1] * 100f).toInt().coerceIn(0, 100)
            binding.seekValue.progress = (hsv[2] * 100f).toInt().coerceIn(0, 100)
        }

        val sliderListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || updatingFromHex) return
                hsv[0] = binding.seekHue.progress.toFloat()
                hsv[1] = binding.seekSaturation.progress / 100f
                hsv[2] = binding.seekValue.progress / 100f
                applyPreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

        binding.seekHue.setOnSeekBarChangeListener(sliderListener)
        binding.seekSaturation.setOnSeekBarChangeListener(sliderListener)
        binding.seekValue.setOnSeekBarChangeListener(sliderListener)

        binding.etHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingFromSliders) return
                val parsed = GroupSetColors.parseHex(s?.toString()) ?: return
                updatingFromHex = true
                Color.colorToHSV(ColorUtils.setAlphaComponent(parsed, 0xFF), hsv)
                applySlidersFromHsv()
                val bg = (ContextCompat.getDrawable(context, R.drawable.bg_color_swatch)
                    ?.mutate() as GradientDrawable)
                bg.setColor(currentColor())
                binding.colorPreview.background = bg
                updatingFromHex = false
            }
        })

        applySlidersFromHsv()
        applyPreview()

        AlertDialog.Builder(context)
            .setTitle(R.string.group_set_color_picker_title)
            .setView(binding.root)
            .setPositiveButton(R.string.confirm) { _, _ ->
                onPicked(ColorUtils.setAlphaComponent(currentColor(), 0xFF))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
