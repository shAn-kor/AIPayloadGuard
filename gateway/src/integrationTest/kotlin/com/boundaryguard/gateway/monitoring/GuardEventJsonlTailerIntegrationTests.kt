package com.boundaryguard.gateway.monitoring

import com.boundaryguard.gateway.audit.GuardEventSeverity
import com.boundaryguard.gateway.audit.InMemoryRecentGuardEventBuffer
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GuardEventJsonlTailerIntegrationTests {
    @TempDir
    lateinit var tempDir: Path

    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val buffer = InMemoryRecentGuardEventBuffer()
    private val broadcaster = RecordingGuardEventSseBroadcaster()

    @Test
    fun `polls rust guard events from jsonl into recent buffer and sse broadcaster`() {
        val eventLog = tempDir.resolve("rust-guard-events.jsonl")
        eventLog.writeText(rustEventLine(eventId = "evt-req-1", requestId = "req-1", severity = "CRITICAL") + "\n")
        val tailer = tailer(eventLog)

        tailer.pollOnce()

        val recent = buffer.recent()
        assertEquals(listOf("evt-req-1"), recent.map { event -> event.eventId })
        assertEquals("req-1", recent.single().requestId)
        assertEquals(GuardEventSeverity.CRITICAL, recent.single().severity)
        assertEquals(listOf("evt-req-1"), broadcaster.sentEvents.map { event -> event.id })
    }

    @Test
    fun `maps rust none severity to unspecified`() {
        val eventLog = tempDir.resolve("rust-guard-events.jsonl")
        eventLog.writeText(rustEventLine(eventId = "evt-req-allow", requestId = "req-allow", severity = "NONE") + "\n")
        val tailer = tailer(eventLog)

        tailer.pollOnce()

        assertEquals(GuardEventSeverity.UNSPECIFIED, buffer.recent().single().severity)
    }

    @Test
    fun `skips malformed lines and continues with valid events`() {
        val eventLog = tempDir.resolve("rust-guard-events.jsonl")
        eventLog.writeText("not-json\n" + rustEventLine(eventId = "evt-req-valid", requestId = "req-valid") + "\n")
        val tailer = tailer(eventLog)

        tailer.pollOnce()

        assertEquals(listOf("evt-req-valid"), buffer.recent().map { event -> event.eventId })
    }

    @Test
    fun `keeps partial json line pending until newline arrives`() {
        val eventLog = tempDir.resolve("rust-guard-events.jsonl")
        val line = rustEventLine(eventId = "evt-req-partial", requestId = "req-partial")
        Files.writeString(eventLog, line)
        val tailer = tailer(eventLog)

        tailer.pollOnce()
        assertTrue(buffer.recent().isEmpty())

        Files.writeString(eventLog, "\n", java.nio.file.StandardOpenOption.APPEND)
        tailer.pollOnce()

        assertEquals(listOf("evt-req-partial"), buffer.recent().map { event -> event.eventId })
    }

    private fun tailer(path: Path): GuardEventJsonlTailer = GuardEventJsonlTailer(
        properties = GuardEventJsonlTailerProperties(enabled = true, path = path),
        objectMapper = objectMapper,
        recentGuardEventBuffer = buffer,
        guardEventSseBroadcaster = broadcaster,
    )

    private fun rustEventLine(
        eventId: String,
        requestId: String,
        severity: String = "CRITICAL",
    ): String = """
        {
          "event_id":"$eventId",
          "request_id":"$requestId",
          "payload_type":"PROMPT",
          "decision":"BLOCK",
          "risk_score":95,
          "severity":"$severity",
          "high_risk":true,
          "policy_revision":"builtin-mvp",
          "content_hash":"sha256:abc123",
          "redacted_summary":"[REDACTED:PII]",
          "redaction_count":1,
          "violations":[
            {
              "policy_id":"prompt_injection.basic",
              "violation_type":"PROMPT_INJECTION",
              "severity":"CRITICAL",
              "message":"Prompt injection pattern detected",
              "start_offset":0,
              "end_offset":28,
              "detector":"prompt_injection"
            }
          ],
          "core_latency_ms":3,
          "created_at":"2026-04-26T00:00:00Z"
        }
    """.trimIndent().lineSequence().joinToString(separator = "") { line -> line.trim() }
}

private class RecordingGuardEventSseBroadcaster : GuardEventSseBroadcaster() {
    val sentEvents = mutableListOf<TailedGuardEvent>()

    override fun emit(event: com.boundaryguard.gateway.audit.GuardEvent) {
        sentEvents += TailedGuardEvent(
            id = event.eventId,
            payload = GuardEventSsePayload.from(event),
        )
    }
}

private data class TailedGuardEvent(
    val id: String,
    val payload: GuardEventSsePayload,
)
