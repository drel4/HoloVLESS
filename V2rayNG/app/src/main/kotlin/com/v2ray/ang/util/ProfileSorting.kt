package com.v2ray.ang.util

import java.util.Locale

object ProfileSorting {
    data class Entry(val guid: String, val name: String, val protocol: String,
                     val delay: Long, val addedTime: Long)

    fun sort(entries: List<Entry>, mode: Int): List<String> {
        val comparator = when (mode) {
            0 -> compareBy<Entry> { it.name.lowercase(Locale.ROOT) }
            // Successful results first, untested next, failed last. Stable on ties.
            1 -> compareBy<Entry> { if (it.delay > 0) 0 else if (it.delay == 0L) 1 else 2 }
                    .thenBy { if (it.delay > 0) it.delay else Long.MAX_VALUE }
            2 -> compareBy<Entry> { it.protocol }.thenBy { it.name.lowercase(Locale.ROOT) }
            else -> compareByDescending<Entry> { it.addedTime }
        }
        return entries.sortedWith(comparator).map { it.guid }
    }
}
