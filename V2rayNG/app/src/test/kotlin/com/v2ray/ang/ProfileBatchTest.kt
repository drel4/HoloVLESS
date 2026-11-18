package com.v2ray.ang

import com.v2ray.ang.util.ProfileBatch
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProfileBatchTest {
    @Test fun defaultReallyRuns32BlockingProbesAtOnceWithoutDuplicates() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val firstBatch = CountDownLatch(32)
        val seen = ConcurrentHashMap.newKeySet<Int>()
        withContext(Dispatchers.IO) {
            withTimeout(10000) {
                ProfileBatch.run((0 until 101).toList()) { item ->
                    val count = active.incrementAndGet()
                    peak.updateAndGet { maxOf(it, count) }
                    firstBatch.countDown()
                    try {
                        assertTrue("32 blocking probes did not overlap", firstBatch.await(5, TimeUnit.SECONDS))
                        Thread.sleep(2)
                        assertTrue("duplicate work item", seen.add(item))
                    } finally { active.decrementAndGet() }
                }
            }
        }
        assertEquals(32, peak.get())
        assertEquals(101, seen.size)
        assertEquals(0, active.get())
    }

    @Test fun smallerLimitsAndCancellationStopQueuedWork() = runBlocking {
        val started = Channel<Int>(Channel.UNLIMITED)
        val count = AtomicInteger()
        val job = launch {
            ProfileBatch.run((0 until 100).toList(), 4) { item ->
                count.incrementAndGet()
                started.send(item)
                delay(Long.MAX_VALUE)
            }
        }
        withTimeout(2000) { repeat(4) { started.receive() } }
        job.cancelAndJoin()
        assertEquals(4, count.get())
        started.close()
        Unit
    }

    @Test fun emptyAndInvalidSettingsAreSafe() = runBlocking {
        assertEquals(32, ProfileBatch.concurrency(null))
        assertEquals(32, ProfileBatch.concurrency("invalid"))
        assertEquals(1, ProfileBatch.concurrency("0"))
        assertEquals(64, ProfileBatch.concurrency("999"))
        ProfileBatch.run(emptyList<Int>()) { fail("empty queue ran a probe") }
    }
}
