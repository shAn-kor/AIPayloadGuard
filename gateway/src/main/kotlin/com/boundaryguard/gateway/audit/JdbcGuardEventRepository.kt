package com.boundaryguard.gateway.audit

import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository

@Repository
class JdbcGuardEventRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) : GuardEventRepository {
    override fun save(event: GuardEvent): GuardEvent {
        jdbcClient.sql(
            """
            INSERT INTO guard_events (
                event_id,
                request_id,
                occurred_at,
                payload_type,
                decision,
                risk_score,
                severity,
                high_risk,
                policy_revision,
                provider_type,
                provider_name,
                provider_model,
                provider_endpoint,
                principal_id,
                tenant_id,
                project_id,
                environment,
                content_hash,
                redacted_summary,
                redaction_count,
                core_latency_ms,
                violations_json
            ) VALUES (
                :eventId,
                :requestId,
                :occurredAt,
                :payloadType,
                :decision,
                :riskScore,
                :severity,
                :highRisk,
                :policyRevision,
                :providerType,
                :providerName,
                :providerModel,
                :providerEndpoint,
                :principalId,
                :tenantId,
                :projectId,
                :environment,
                :contentHash,
                :redactedSummary,
                :redactionCount,
                :coreLatencyMs,
                :violationsJson
            )
            """.trimIndent(),
        )
            .param("eventId", event.eventId)
            .param("requestId", event.requestId)
            .param("occurredAt", Timestamp.from(event.occurredAt))
            .param("payloadType", event.payloadType.name)
            .param("decision", event.decision.name)
            .param("riskScore", event.riskScore)
            .param("severity", event.severity.name)
            .param("highRisk", event.highRisk)
            .param("policyRevision", event.policyRevision)
            .param("providerType", event.provider?.providerType)
            .param("providerName", event.provider?.providerName)
            .param("providerModel", event.provider?.model)
            .param("providerEndpoint", event.provider?.endpoint)
            .param("principalId", event.principal?.principalId)
            .param("tenantId", event.principal?.tenantId)
            .param("projectId", event.principal?.projectId)
            .param("environment", event.principal?.environment)
            .param("contentHash", event.contentHash)
            .param("redactedSummary", event.redactedSummary)
            .param("redactionCount", event.redactionCount)
            .param("coreLatencyMs", event.coreLatencyMs)
            .param("violationsJson", objectMapper.writeValueAsString(event.violations))
            .update()
        return event
    }

    fun findByRequestId(requestId: String): GuardEvent? = jdbcClient.sql(
        """
        SELECT *
        FROM guard_events
        WHERE request_id = :requestId
        ORDER BY occurred_at DESC
        LIMIT 1
        """.trimIndent(),
    )
        .param("requestId", requestId)
        .query { rs, _ -> rs.toGuardEvent() }
        .optional()
        .orElse(null)

    fun count(): Long = jdbcClient.sql("SELECT COUNT(*) FROM guard_events")
        .query(Long::class.java)
        .single()

    fun deleteAll() {
        jdbcClient.sql("DELETE FROM guard_events").update()
    }

    fun containsTextInPersistenceColumns(value: String): Boolean = jdbcClient.sql(
        """
        SELECT COUNT(*)
        FROM guard_events
        WHERE content_hash LIKE :value
           OR COALESCE(redacted_summary, '') LIKE :value
           OR COALESCE(violations_json, '') LIKE :value
        """.trimIndent(),
    )
        .param("value", "%$value%")
        .query(Long::class.java)
        .single() > 0

    fun ResultSet.toGuardEvent(): GuardEvent = GuardEvent(
        eventId = getString("event_id"),
        requestId = getString("request_id"),
        occurredAt = getTimestamp("occurred_at").toInstant(),
        payloadType = GuardEventPayloadType.valueOf(getString("payload_type")),
        decision = GuardEventDecision.valueOf(getString("decision")),
        riskScore = getInt("risk_score"),
        severity = GuardEventSeverity.valueOf(getString("severity")),
        highRisk = getBoolean("high_risk"),
        policyRevision = getString("policy_revision"),
        provider = getString("provider_type")?.let { providerType ->
            GuardEventProvider(
                providerType = providerType,
                providerName = getString("provider_name"),
                model = getString("provider_model"),
                endpoint = getString("provider_endpoint"),
            )
        },
        principal = GuardEventPrincipal(
            principalId = getString("principal_id"),
            tenantId = getString("tenant_id"),
            projectId = getString("project_id"),
            environment = getString("environment"),
        ),
        contentHash = getString("content_hash"),
        redactedSummary = getString("redacted_summary"),
        redactionCount = getInt("redaction_count"),
        coreLatencyMs = getLong("core_latency_ms"),
        violations = objectMapper.readValue(
            getString("violations_json"),
            objectMapper.typeFactory.constructCollectionType(List::class.java, GuardEventViolation::class.java),
        ),
    )
}
