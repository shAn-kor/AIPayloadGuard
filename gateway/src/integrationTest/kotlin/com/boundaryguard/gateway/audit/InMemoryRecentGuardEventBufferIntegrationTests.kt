package com.boundaryguard.gateway.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InMemoryRecentGuardEventBufferIntegrationTests {
    @Test
    fun `keeps recent events in insertion order`() {
        val buffer = InMemoryRecentGuardEventBuffer()
        val first = GuardEventFixtures.blockEvent(eventId = "event-1", requestId = "req-1")
        val second = GuardEventFixtures.blockEvent(eventId = "event-2", requestId = "req-2")

        buffer.publish(first)
        buffer.publish(second)

        assertEquals(listOf("event-1", "event-2"), buffer.recent().map { it.eventId })
        assertEquals(listOf("event-2"), buffer.recent(limit = 1).map { it.eventId })
    }
}
