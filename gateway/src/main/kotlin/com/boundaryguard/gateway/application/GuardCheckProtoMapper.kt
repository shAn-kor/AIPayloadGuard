package com.boundaryguard.gateway.application

import com.boundaryguard.contract.guard.v1.DecisionType
import com.boundaryguard.contract.guard.v1.GuardCheckRequest
import com.boundaryguard.contract.guard.v1.GuardCheckResult
import com.boundaryguard.contract.guard.v1.PayloadType
import com.boundaryguard.contract.guard.v1.ProviderType
import com.boundaryguard.contract.guard.v1.Severity
import com.boundaryguard.contract.guard.v1.ViolationType
import com.boundaryguard.contract.guard.v1.guardCheckRequest
import com.boundaryguard.contract.guard.v1.principalContext
import com.boundaryguard.contract.guard.v1.providerMetadata
import com.boundaryguard.gateway.api.DecisionDto
import com.boundaryguard.gateway.api.GuardCheckHttpRequest
import com.boundaryguard.gateway.api.GuardCheckHttpResponse
import com.boundaryguard.gateway.api.PayloadTypeDto
import com.boundaryguard.gateway.api.PrincipalContextDto
import com.boundaryguard.gateway.api.ProviderMetadataDto
import com.boundaryguard.gateway.api.ProviderTypeDto
import com.boundaryguard.gateway.api.RedactionResultDto
import com.boundaryguard.gateway.api.RedactionSpanDto
import com.boundaryguard.gateway.api.SeverityDto
import com.boundaryguard.gateway.api.ViolationEvidenceDto
import com.boundaryguard.gateway.api.ViolationTypeDto
import org.springframework.stereotype.Component

@Component
class GuardCheckProtoMapper {
    fun toProto(request: GuardCheckHttpRequest): GuardCheckRequest = guardCheckRequest {
        requestId = request.requestId
        payloadType = toProto(request.payloadType)
        content = request.content
        policyRevision = request.policyRevision.orEmpty()
        metadata.putAll(request.metadata.orEmpty())
        request.providerMetadata?.let { providerMetadata = toProto(it) }
        request.principalContext?.let { principalContext = toProto(it) }
    }

    fun toHttpResponse(result: GuardCheckResult): GuardCheckHttpResponse = GuardCheckHttpResponse(
        requestId = result.requestId,
        decision = toDto(result.decision),
        riskScore = result.riskScore,
        violations = result.violationsList.map { violation ->
            ViolationEvidenceDto(
                policyId = violation.policyId,
                violationType = toDto(violation.violationType),
                severity = toDto(violation.severity),
                message = violation.message,
                startOffset = violation.startOffset,
                endOffset = violation.endOffset,
                detector = violation.detector,
            )
        },
        redactionResult = if (result.hasRedactionResult()) {
            RedactionResultDto(
                redacted = result.redactionResult.redacted,
                redactedContent = result.redactionResult.redactedContent,
                redactionCount = result.redactionResult.redactionCount,
                spans = result.redactionResult.spansList.map { span ->
                    RedactionSpanDto(
                        startOffset = span.startOffset,
                        endOffset = span.endOffset,
                        replacement = span.replacement,
                        violationType = toDto(span.violationType),
                    )
                },
            )
        } else {
            null
        },
        policyRevision = result.policyRevision,
        coreLatencyMs = result.coreLatencyMs,
    )

    fun toProto(payloadType: PayloadTypeDto): PayloadType = when (payloadType) {
        PayloadTypeDto.TEXT -> PayloadType.PAYLOAD_TYPE_TEXT
        PayloadTypeDto.PROMPT -> PayloadType.PAYLOAD_TYPE_PROMPT
        PayloadTypeDto.RESPONSE -> PayloadType.PAYLOAD_TYPE_RESPONSE
        PayloadTypeDto.DATA_EGRESS -> PayloadType.PAYLOAD_TYPE_DATA_EGRESS
    }

    fun toProto(providerMetadata: ProviderMetadataDto) = providerMetadata {
        providerType = toProto(providerMetadata.providerType)
        providerName = providerMetadata.providerName.orEmpty()
        model = providerMetadata.model.orEmpty()
        endpoint = providerMetadata.endpoint.orEmpty()
    }

    fun toProto(principalContext: PrincipalContextDto) = principalContext {
        principalId = principalContext.principalId.orEmpty()
        tenantId = principalContext.tenantId.orEmpty()
        projectId = principalContext.projectId.orEmpty()
        roles += principalContext.roles
        environment = principalContext.environment.orEmpty()
    }

    fun toProto(providerType: ProviderTypeDto): ProviderType = when (providerType) {
        ProviderTypeDto.OPENAI_COMPATIBLE -> ProviderType.PROVIDER_TYPE_OPENAI_COMPATIBLE
        ProviderTypeDto.ANTHROPIC -> ProviderType.PROVIDER_TYPE_ANTHROPIC
        ProviderTypeDto.GOOGLE -> ProviderType.PROVIDER_TYPE_GOOGLE
        ProviderTypeDto.INTERNAL -> ProviderType.PROVIDER_TYPE_INTERNAL
    }

    fun toDto(decision: DecisionType): DecisionDto = when (decision) {
        DecisionType.DECISION_TYPE_ALLOW -> DecisionDto.ALLOW
        DecisionType.DECISION_TYPE_REDACT -> DecisionDto.REDACT
        DecisionType.DECISION_TYPE_BLOCK -> DecisionDto.BLOCK
        DecisionType.DECISION_TYPE_UNSPECIFIED,
        DecisionType.UNRECOGNIZED,
        -> DecisionDto.BLOCK
    }

    fun toDto(violationType: ViolationType): ViolationTypeDto = when (violationType) {
        ViolationType.VIOLATION_TYPE_PROMPT_INJECTION -> ViolationTypeDto.PROMPT_INJECTION
        ViolationType.VIOLATION_TYPE_PII -> ViolationTypeDto.PII
        ViolationType.VIOLATION_TYPE_SECRET -> ViolationTypeDto.SECRET
        ViolationType.VIOLATION_TYPE_POLICY -> ViolationTypeDto.POLICY
        ViolationType.VIOLATION_TYPE_UNSPECIFIED,
        ViolationType.UNRECOGNIZED,
        -> ViolationTypeDto.UNSPECIFIED
    }

    fun toDto(severity: Severity): SeverityDto = when (severity) {
        Severity.SEVERITY_LOW -> SeverityDto.LOW
        Severity.SEVERITY_MEDIUM -> SeverityDto.MEDIUM
        Severity.SEVERITY_HIGH -> SeverityDto.HIGH
        Severity.SEVERITY_CRITICAL -> SeverityDto.CRITICAL
        Severity.SEVERITY_UNSPECIFIED,
        Severity.UNRECOGNIZED,
        -> SeverityDto.UNSPECIFIED
    }
}
