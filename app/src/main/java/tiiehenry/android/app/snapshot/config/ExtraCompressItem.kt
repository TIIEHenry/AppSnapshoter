package tiiehenry.android.app.snapshot.config

data class ExtraCompressItem(
    val name: String,
    val path: String,
    val excludes: List<String> = emptyList()
)