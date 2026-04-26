package com.boundaryguard.gateway.monitoring

import com.boundaryguard.gateway.audit.GuardEvent
import com.boundaryguard.gateway.audit.GuardEventDecision
import com.boundaryguard.gateway.audit.GuardEventPayloadType
import com.boundaryguard.gateway.audit.GuardEventSeverity
import com.boundaryguard.gateway.audit.InMemoryRecentGuardEventBuffer
import org.springframework.stereotype.Service

private const val DEFAULT_RECENT_EVENT_LIMIT = 100

@Service
class GuardEventMonitoringService(
    private val recentGuardEventBuffer: InMemoryRecentGuardEventBuffer,
) {
    fun dashboard(limit: Int = DEFAULT_RECENT_EVENT_LIMIT): GuardEventDashboardView {
        val events = recentGuardEventBuffer.recent(limit).map { event -> event.toView() }
        return GuardEventDashboardView(
            totalCount = events.size,
            blockCount = events.count { event -> event.decision == GuardEventDecision.BLOCK },
            redactCount = events.count { event -> event.decision == GuardEventDecision.REDACT },
            highRiskCount = events.count { event -> event.highRisk },
            recentEvents = events.asReversed(),
        )
    }

    fun detail(eventId: String): GuardEventView? = recentGuardEventBuffer.recent()
        .firstOrNull { event -> event.eventId == eventId }
        ?.toView()

    private fun GuardEvent.toView(): GuardEventView = GuardEventView(
        eventId = eventId,
        requestId = requestId,
        occurredAt = occurredAt.toString(),
        payloadType = payloadType,
        decision = decision,
        riskScore = riskScore,
        severity = severity,
        highRisk = highRisk,
        policyRevision = policyRevision,
        providerType = provider?.providerType,
        providerName = provider?.providerName,
        model = provider?.model,
        principalId = principal?.principalId,
        tenantId = principal?.tenantId,
        projectId = principal?.projectId,
        environment = principal?.environment,
        contentHash = contentHash,
        redactedSummary = redactedSummary,
        redactionCount = redactionCount,
        coreLatencyMs = coreLatencyMs,
        violations = violations.map { violation ->
            GuardEventViolationView(
                policyId = violation.policyId,
                violationType = violation.violationType,
                severity = violation.severity,
                message = violation.message,
                startOffset = violation.startOffset,
                endOffset = violation.endOffset,
                detector = violation.detector,
            )
        },
    )
}

data class GuardEventDashboardView(
    val totalCount: Int,
    val blockCount: Int,
    val redactCount: Int,
    val highRiskCount: Int,
    val recentEvents: List<GuardEventView>,
)

data class GuardEventView(
    val eventId: String,
    val requestId: String,
    val occurredAt: String,
    val payloadType: GuardEventPayloadType,
    val decision: GuardEventDecision,
    val riskScore: Int,
    val severity: GuardEventSeverity,
    val highRisk: Boolean,
    val policyRevision: String,
    val providerType: String?,
    val providerName: String?,
    val model: String?,
    val principalId: String?,
    val tenantId: String?,
    val projectId: String?,
    val environment: String?,
    val contentHash: String,
    val redactedSummary: String?,
    val redactionCount: Int,
    val coreLatencyMs: Long,
    val violations: List<GuardEventViolationView>,
)

data class GuardEventViolationView(
    val policyId: String,
    val violationType: String,
    val severity: GuardEventSeverity,
    val message: String,
    val startOffset: Int,
    val endOffset: Int,
    val detector: String,
)
