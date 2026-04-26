package com.boundaryguard.gateway.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompositeGuardEventSinkIntegrationTests {
    @Test
    fun `publishes event to every delegate sink`() {
        val first = CapturingSink()
        val second = CapturingSink()
        val sink = CompositeGuardEventSink(listOf(first, second))
        val event = GuardEventFixtures.blockEvent()

        sink.publish(event)

        assertEquals(listOf(event), first.events)
        assertEquals(listOf(event), second.events)
    }

    private class CapturingSink : GuardEventSink {
        val events = mutableListOf<GuardEvent>()

        override fun publish(event: GuardEvent) {
            events += event
        }
    }
}
