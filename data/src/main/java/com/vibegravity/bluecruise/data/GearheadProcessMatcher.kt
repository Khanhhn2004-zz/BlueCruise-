package com.vibegravity.bluecruise.data

object GearheadProcessMatcher {
    private const val BASE_PROCESS = "com.google.android.projection.gearhead"
    private const val SUBPROCESS_PREFIX = "$BASE_PROCESS:"

    fun matches(processName: String?): Boolean {
        if (processName == null) return false
        if (processName == BASE_PROCESS) return true
        return processName.startsWith(SUBPROCESS_PREFIX) && processName.length > SUBPROCESS_PREFIX.length
    }
}
