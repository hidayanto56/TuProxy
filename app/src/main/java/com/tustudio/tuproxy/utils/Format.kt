package com.tustudio.tuproxy.utils

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.2f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.2f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
