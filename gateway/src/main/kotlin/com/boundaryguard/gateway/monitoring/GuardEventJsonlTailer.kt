package com.boundaryguard.gateway.monitoring

import com.boundaryguard.gateway.audit.GuardEvent
import com.boundaryguard.gateway.audit.GuardEventDecision
import com.boundaryguard.gateway.audit.GuardEventPayloadType
import com.boundaryguard.gateway.audit.GuardEventPrincipal
import com.boundaryguard.gateway.audit.GuardEventProvider
import com.boundaryguard.gateway.audit.GuardEventSeverity
import com.boundaryguard.gateway.audit.GuardEventViolation
import com.boundaryguard.gateway.audit.InMemoryRecentGuardEventBuffer
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val READ_BUFFER_SIZE = 8192

@ConfigurationProperties(prefix = "boundary.guard-events.tailer")
data class GuardEventJsonlTailerProperties(
    val enabled: Boolean = false,
    val path: Path = Path.of("logs", "rust-guard-events.jsonl"),
)

@Component
@EnableConfigurationProperties(GuardEventJsonlTailerProperties::class)
class GuardEventJsonlTailer(
    private val properties: GuardEventJsonlTailerProperties,
    private val objectMapper: ObjectMapper,
    private val recentGuardEventBuffer: InMemoryRecentGuardEventBuffer,
    private val guardEventSseBroadcaster: GuardEventSseBroadcaster,
) {
    private var offset: Long = 0
    private var pendingPartialLine = ""

    @Scheduled(fixedDelayString = "\${boundary.guard-events.tailer.poll-delay-ms:1000}")
    fun pollIfEnabled() {
        if (properties.enabled) {
            pollOnce()
        }
    }

    fun pollOnce() {
        val path = properties.path
        if (!Files.exists(path)) {
            return
        }

        val currentSize = Files.size(path)
        if (currentSize < offset) {
            offset = 0
            pendingPartialLine = ""
        }
        if (currentSize == offset) {
            return
        }

        Files.newByteChannel(path, StandardOpenOption.READ).use { channel ->
            channel.position(offset)
            val readResult = channel.readAvailableText()
            offset = channel.position()
            consumeText(readResult)
        }
    }

    private fun SeekableByteChannel.readAvailableText(): String {
        val buffer = ByteBuffer.allocate(READ_BUFFER_SIZE)
        val builder = StringBuilder()
        while (read(buffer) > 0) {
            buffer.flip()
            builder.append(StandardCharsets.UTF_8.decode(buffer))
            buffer.clear()
        }
        return builder.toString()
    }

    private fun consumeText(text: String) {
        val merged = pendingPartialLine + text
        val endsWithNewline = merged.endsWith('\n') || merged.endsWith('\r')
        val lines = merged.lineSequence().toList()

        val completeLines = if (endsWithNewline) {
            pendingPartialLine = ""
            lines
        } else {
            pendingPartialLine = lines.lastOrNull().orEmpty()
            lines.dropLast(1)
        }

        completeLines
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .forEach { line -> publishLine(line) }
    }

    private fun publishLine(line: String) {
        runCatching {
            objectMapper.readValue(line, RustGuardEventLine::class.java).toGuardEvent()
        }.onSuccess { event ->
            recentGuardEventBuffer.publishIfAbsent(event)
            guardEventSseBroadcaster.publish(event)
        }.onFailure { error ->
            log.warn("guard_event_jsonl_tail_failed path={} offset={}", properties.path, offset, error)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GuardEventJsonlTailer::class.java)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RustGuardEventLine(
    @JsonProperty("event_id") val eventId: String,
    @JsonProperty("request_id") val requestId: String,
    @JsonProperty("payload_type") val payloadType: String,
    @JsonProperty("decision") val decision: String,
    @JsonProperty("risk_score") val riskScore: Int,
    @JsonProperty("severity") val severity: String,
    @JsonProperty("high_risk") val highRisk: Boolean,
    @JsonProperty("policy_revision") val policyRevision: String,
    @JsonProperty("content_hash") val contentHash: String,
    @JsonProperty("redacted_summary") val redactedSummary: String? = null,
    @JsonProperty("redaction_count") val redactionCount: Int = 0,
    @JsonProperty("violations") val violations: List<RustGuardEventViolationLine> = emptyList(),
    @JsonProperty("core_latency_ms") val coreLatencyMs: Long = 0,
    @JsonProperty("created_at") val createdAt: Instant,
) {
    fun toGuardEvent(): GuardEvent = GuardEvent(
        eventId = eventId,
        requestId = requestId,
        occurredAt = createdAt,
        payloadType = GuardEventPayloadType.valueOf(payloadType),
        decision = GuardEventDecision.valueOf(decision),
        riskScore = riskScore,
        severity = severity.toGuardEventSeverity(),
        highRisk = highRisk,
        policyRevision = policyRevision,
        provider = null,
        principal = null,
        contentHash = contentHash,
        redactedSummary = redactedSummary,
        redactionCount = redactionCount,
        coreLatencyMs = coreLatencyMs,
        violations = violations.map { violation -> violation.toGuardEventViolation() },
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class RustGuardEventViolationLine(
    @JsonProperty("policy_id") val policyId: String,
    @JsonProperty("violation_type") val violationType: String,
    @JsonProperty("severity") val severity: String,
    @JsonProperty("message") val message: String,
    @JsonProperty("start_offset") val startOffset: Int,
    @JsonProperty("end_offset") val endOffset: Int,
    @JsonProperty("detector") val detector: String,
) {
    fun toGuardEventViolation(): GuardEventViolation = GuardEventViolation(
        policyId = policyId,
        violationType = violationType.uppercase(),
        severity = severity.toGuardEventSeverity(),
        message = message,
        startOffset = startOffset,
        endOffset = endOffset,
        detector = detector,
    )
}

private fun String.toGuardEventSeverity(): GuardEventSeverity = when (uppercase()) {
    "LOW" -> GuardEventSeverity.LOW
    "MEDIUM" -> GuardEventSeverity.MEDIUM
    "HIGH" -> GuardEventSeverity.HIGH
    "CRITICAL" -> GuardEventSeverity.CRITICAL
    "NONE", "UNSPECIFIED" -> GuardEventSeverity.UNSPECIFIED
    else -> GuardEventSeverity.UNSPECIFIED
}

