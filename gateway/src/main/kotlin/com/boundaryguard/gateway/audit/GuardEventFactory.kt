package com.boundaryguard.gateway.audit

import com.boundaryguard.gateway.api.DecisionDto
import com.boundaryguard.gateway.api.GuardCheckHttpRequest
import com.boundaryguard.gateway.api.GuardCheckHttpResponse
import com.boundaryguard.gateway.api.PayloadTypeDto
import com.boundaryguard.gateway.api.SeverityDto
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class GuardEventFactory(
    private val clock: Clock,
) {
    fun create(request: GuardCheckHttpRequest, response: GuardCheckHttpResponse): GuardEvent {
        val severity = GuardEvent.severityFor(response.riskScore)
        return GuardEvent(
            eventId = UUID.randomUUID().toString(),
            requestId = response.requestId,
            occurredAt = Instant.now(clock),
            payloadType = request.payloadType.toGuardEventPayloadType(),
            decision = response.decision.toGuardEventDecision(),
            riskScore = response.riskScore,
            severity = severity,
            highRisk = GuardEvent.isHighRisk(response.riskScore),
            policyRevision = response.policyRevision,
            provider = request.providerMetadata?.let { provider ->
                GuardEventProvider(
                    providerType = provider.providerType.name,
                    providerName = provider.providerName,
                    model = provider.model,
                    endpoint = provider.endpoint,
                )
            },
            principal = request.principalContext?.let { principal ->
                GuardEventPrincipal(
                    principalId = principal.principalId,
                    tenantId = principal.tenantId,
                    projectId = principal.projectId,
                    environment = principal.environment,
                )
            },
            contentHash = sha256(request.content),
            redactedSummary = response.redactionResult?.redactedContent?.takeIf { response.redactionResult.redacted },
            redactionCount = response.redactionResult?.redactionCount ?: 0,
            coreLatencyMs = response.coreLatencyMs,
            violations = response.violations.map { violation ->
                GuardEventViolation(
                    policyId = violation.policyId,
                    violationType = violation.violationType.name,
                    severity = violation.severity.toGuardEventSeverity(),
                    message = violation.message,
                    startOffset = violation.startOffset,
                    endOffset = violation.endOffset,
                    detector = violation.detector,
                )
            },
        )
    }
}

fun defaultClock(): Clock = Clock.systemUTC()

fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

fun PayloadTypeDto.toGuardEventPayloadType(): GuardEventPayloadType = when (this) {
    PayloadTypeDto.TEXT -> GuardEventPayloadType.TEXT
    PayloadTypeDto.PROMPT -> GuardEventPayloadType.PROMPT
    PayloadTypeDto.RESPONSE -> GuardEventPayloadType.RESPONSE
    PayloadTypeDto.DATA_EGRESS -> GuardEventPayloadType.DATA_EGRESS
}

fun DecisionDto.toGuardEventDecision(): GuardEventDecision = when (this) {
    DecisionDto.ALLOW -> GuardEventDecision.ALLOW
    DecisionDto.REDACT -> GuardEventDecision.REDACT
    DecisionDto.BLOCK -> GuardEventDecision.BLOCK
}

fun SeverityDto.toGuardEventSeverity(): GuardEventSeverity = when (this) {
    SeverityDto.LOW -> GuardEventSeverity.LOW
    SeverityDto.MEDIUM -> GuardEventSeverity.MEDIUM
    SeverityDto.HIGH -> GuardEventSeverity.HIGH
    SeverityDto.CRITICAL -> GuardEventSeverity.CRITICAL
    SeverityDto.UNSPECIFIED -> GuardEventSeverity.UNSPECIFIED
}
