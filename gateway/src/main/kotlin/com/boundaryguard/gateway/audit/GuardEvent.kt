package com.boundaryguard.gateway.audit

import java.time.Instant

private const val HIGH_RISK_THRESHOLD = 80

data class GuardEvent(
    val eventId: String,
    val requestId: String,
    val occurredAt: Instant,
    val payloadType: GuardEventPayloadType,
    val decision: GuardEventDecision,
    val riskScore: Int,
    val severity: GuardEventSeverity,
    val highRisk: Boolean,
    val policyRevision: String,
    val provider: GuardEventProvider?,
    val principal: GuardEventPrincipal?,
    val contentHash: String,
    val redactedSummary: String?,
    val redactionCount: Int,
    val coreLatencyMs: Long,
    val violations: List<GuardEventViolation>,
) {
    init {
        require(eventId.isNotBlank()) { "eventId must not be blank" }
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        require(riskScore in 0..100) { "riskScore must be between 0 and 100" }
        require(contentHash.isNotBlank()) { "contentHash must not be blank" }
        require(redactionCount >= 0) { "redactionCount must not be negative" }
        require(coreLatencyMs >= 0) { "coreLatencyMs must not be negative" }
    }

    companion object {
        fun severityFor(riskScore: Int): GuardEventSeverity = when {
            riskScore >= 90 -> GuardEventSeverity.CRITICAL
            riskScore >= 70 -> GuardEventSeverity.HIGH
            riskScore >= 40 -> GuardEventSeverity.MEDIUM
            else -> GuardEventSeverity.LOW
        }

        fun isHighRisk(riskScore: Int): Boolean = riskScore >= HIGH_RISK_THRESHOLD
    }
}

data class GuardEventProvider(
    val providerType: String,
    val providerName: String?,
    val model: String?,
    val endpoint: String?,
)

data class GuardEventPrincipal(
    val principalId: String?,
    val tenantId: String?,
    val projectId: String?,
    val environment: String?,
)

data class GuardEventViolation(
    val policyId: String,
    val violationType: String,
    val severity: GuardEventSeverity,
    val message: String,
    val startOffset: Int,
    val endOffset: Int,
    val detector: String,
)

enum class GuardEventPayloadType {
    TEXT,
    PROMPT,
    RESPONSE,
    DATA_EGRESS,
}

enum class GuardEventDecision {
    ALLOW,
    REDACT,
    BLOCK,
}

enum class GuardEventSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    UNSPECIFIED,
}
