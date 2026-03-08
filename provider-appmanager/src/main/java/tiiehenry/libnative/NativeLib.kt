package tiiehenry.libnative

object NativeLib {
    // For now, we'll implement these as stubs or use Kotlin equivalents
    // The actual native implementation would be provided separately
    
    fun calculateTreeSize(path: String): Long {
        return calculateTreeSizeKotlin(path)
    }
    
    fun getUidGid(path: String): IntArray {
        // Return dummy values since we can't access this without root
        return intArrayOf(-1, -1)
    }
    
    private fun calculateTreeSizeKotlin(path: String): Long {
        val file = java.io.File(path)
        if (!file.exists()) return 0L
        
        if (file.isFile) return file.length()
        
        if (file.isDirectory) {
            var total = 0L
            file.walkTopDown().forEach { f ->
                if (f.isFile) {
                    total += f.length()
                }
            }
            return total
        }
        
        return 0L
    }
}