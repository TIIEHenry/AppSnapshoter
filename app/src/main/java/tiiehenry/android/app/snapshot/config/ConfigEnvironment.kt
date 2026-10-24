package tiiehenry.android.app.snapshot.config

import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.group.GroupSetColors

/**
 * 全局根与强调色的解析入口。
 * 单测可在构造配置对象之前装上覆盖，避免碰到 SnapshotApp / Android 颜色 API。
 */
internal object ConfigEnvironment {
    private var globalRootPathOverride: (() -> String)? = null
    private var parseAccentOverride: ((String?) -> Int?)? = null
    private var defaultAccentOverride: ((String) -> Int)? = null
    private var formatAccentOverride: ((Int) -> String)? = null

    fun globalRootPath(): String {
        val override = globalRootPathOverride
        if (override != null) return override()
        return SnapshotApp.getInstance().globalRootPath
    }

    fun parseAccentColor(hex: String?): Int? {
        val override = parseAccentOverride
        if (override != null) return override(hex)
        return GroupSetColors.parseHex(hex)
    }

    fun defaultAccentColor(setId: String): Int {
        val override = defaultAccentOverride
        if (override != null) return override(setId)
        return GroupSetColors.defaultFor(setId)
    }

    fun formatAccentColor(color: Int): String {
        val override = formatAccentOverride
        if (override != null) return override(color)
        return GroupSetColors.toHex(color)
    }

    fun setGlobalRootPathForTest(provider: () -> String) {
        globalRootPathOverride = provider
    }

    fun setAccentColorForTest(
        parse: (String?) -> Int?,
        defaultColor: (String) -> Int,
        format: (Int) -> String,
    ) {
        parseAccentOverride = parse
        defaultAccentOverride = defaultColor
        formatAccentOverride = format
    }

    fun resetForTest() {
        globalRootPathOverride = null
        parseAccentOverride = null
        defaultAccentOverride = null
        formatAccentOverride = null
    }
}
