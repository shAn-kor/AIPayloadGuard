package com.boundaryguard.gateway.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonlGuardEventSinkIntegrationTests {
    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())

    @Test
    fun `appends guard event as jsonl without original content`() {
        val eventLog = Files.createTempFile("guard-events", ".jsonl")
        val sink = JsonlGuardEventSink(eventLog, objectMapper)
        val event = GuardEventFixtures.blockEvent()

        sink.publish(event)

        val lines = Files.readAllLines(eventLog)
        assertEquals(1, lines.size)
        assertFalse(lines.single().contains("ignore previous instructions"))
        assertFalse(lines.single().contains("user@example.com"))
        assertTrue(lines.single().contains("prompt_injection.basic"))
        assertTrue(lines.single().contains("\"eventId\":\"event-1\""))
        assertTrue(lines.single().contains("\"requestId\":\"req-block-1\""))
        assertTrue(lines.single().contains("\"decision\":\"BLOCK\""))
    }
}
