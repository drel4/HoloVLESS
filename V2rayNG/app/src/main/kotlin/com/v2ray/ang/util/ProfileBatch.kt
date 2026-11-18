package com.v2ray.ang.util

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** A bounded worker queue; slow probes do not hold up an entire fixed chunk. */
object ProfileBatch {
    const val DEFAULT_CONCURRENCY = 32
    const val PREFERENCE = "pref_check_concurrency"

    fun concurrency(value: String?): Int =
            (value?.toIntOrNull() ?: DEFAULT_CONCURRENCY).coerceIn(1, 64)

    suspend fun <T> run(items: List<T>, concurrency: Int = DEFAULT_CONCURRENCY,
                        check: suspend (T) -> Unit) = coroutineScope {
        val next = AtomicInteger()
        repeat(minOf(concurrency.coerceIn(1, 64), items.size)) {
            launch {
                while (true) {
                    ensureActive()
                    val index = next.getAndIncrement()
                    if (index >= items.size) break
                    check(items[index])
                }
            }
        }
    }
}
