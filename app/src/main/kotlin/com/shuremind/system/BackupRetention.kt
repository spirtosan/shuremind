package com.shuremind.system

/** D-32: pure retention logic over file names — keep the newest N `shuremind-backup-*.json` files. */
object BackupRetention {
    const val DEFAULT_RETENTION = 7
    private val NAME_REGEX = Regex("""^shuremind-backup-(\d{8}-\d{6})\.json$""")

    /** Names (from [existingNames]) to delete so only the newest [keep] matching files remain. Non-matching names are left untouched. */
    fun namesToDelete(existingNames: List<String>, keep: Int = DEFAULT_RETENTION): Set<String> {
        val matching = existingNames.filter { NAME_REGEX.matches(it) }
        if (matching.size <= keep) return emptySet()
        val newestFirst = matching.sortedByDescending { NAME_REGEX.find(it)!!.groupValues[1] }
        return newestFirst.drop(keep).toSet()
    }
}
