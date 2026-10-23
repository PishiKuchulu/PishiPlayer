package dev.pishi.player.api

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekPolicyTest {
    @Test fun defaultsAreTvFriendlyAndPositive() {
        val policy = SeekPolicy()
        assertEquals(10_000L, policy.shortPressMs)
        assertEquals(5_000L, policy.longPressIntervalMs)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroSeek() { SeekPolicy(shortPressMs = 0) }
}
