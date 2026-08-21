package tiiehenry.android.app.snapshot.group

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * 分组集强调色：预设板 + 按 setId 稳定默认色。
 */
object GroupSetColors {
    /** ARGB 预设，设置页可选 */
    val PRESETS: IntArray = intArrayOf(
        0xFF0078D4.toInt(), // Fluent blue
        0xFF00BCF2.toInt(), // light blue
        0xFF038387.toInt(), // dark teal
        0xFF00B294.toInt(), // teal
        0xFF00CC6A.toInt(), // mint
        0xFF107C10.toInt(), // green
        0xFFBAD80A.toInt(), // lime
        0xFFFFB900.toInt(), // gold
        0xFFFF8C00.toInt(), // orange
        0xFFD83B01.toInt(), // rust
        0xFFE81123.toInt(), // red
        0xFFE3008C.toInt(), // pink
        0xFFC239B3.toInt(), // magenta
        0xFF8764B8.toInt(), // purple
        0xFF5C2D91.toInt(), // deep purple
        0xFF004E8C.toInt(), // navy
        0xFF69797E.toInt(), // blue gray
        0xFF8A8886.toInt(), // gray
    )

    fun defaultFor(setId: String): Int {
        if (setId.isEmpty()) return PRESETS[0]
        val idx = (setId.hashCode() and Int.MAX_VALUE) % PRESETS.size
        return PRESETS[idx]
    }

    fun parseHex(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        return try {
            val normalized = if (hex.startsWith("#")) hex else "#$hex"
            Color.parseColor(normalized)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** 含 alpha，写入 groupset.json */
    fun toHex(color: Int): String = String.format("#%08X", color)

    /** 用户编辑用 #RRGGBB */
    fun toRgbHex(color: Int): String = String.format("#%06X", color and 0xFFFFFF)

    fun isPreset(color: Int): Boolean {
        val opaque = ColorUtils.setAlphaComponent(color, 0xFF)
        return PRESETS.any { ColorUtils.setAlphaComponent(it, 0xFF) == opaque }
    }

    fun sameColor(a: Int, b: Int): Boolean =
        ColorUtils.setAlphaComponent(a, 0xFF) == ColorUtils.setAlphaComponent(b, 0xFF)
}
