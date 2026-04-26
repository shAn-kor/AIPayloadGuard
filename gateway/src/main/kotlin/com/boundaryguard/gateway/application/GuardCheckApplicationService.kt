package com.boundaryguard.gateway.application

import com.boundaryguard.contract.guard.v1.DecisionType
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
import com.boundaryguard.gateway.coreclient.RustCoreClient
import org.springframework.stereotype.Service

@Service
class GuardCheckApplicationService(
    private val rustCoreClient: RustCoreClient,
) {
    suspend fun check(request: GuardCheckHttpRequest): GuardCheckHttpResponse {
        val coreResult = rustCoreClient.check(request.toProto())
        return coreResult.toHttpResponse()
    }
}

private fun GuardCheckHttpRequest.toProto() = guardCheckRequest {
    requestId = this@toProto.requestId
    payloadType = this@toProto.payloadType.toProto()
    content = this@toProto.content
    policyRevision = this@toProto.policyRevision.orEmpty()
    metadata.putAll(this@toProto.metadata.orEmpty())
    this@toProto.providerMetadata?.let { providerMetadata = it.toProto() }
    this@toProto.principalContext?.let { principalContext = it.toProto() }
}

private fun ProviderMetadataDto.toProto() = providerMetadata {
    providerType = this@toProto.providerType.toProto()
    providerName = this@toProto.providerName.orEmpty()
    model = this@toProto.model.orEmpty()
    endpoint = this@toProto.endpoint.orEmpty()
}

private fun PrincipalContextDto.toProto() = principalContext {
    principalId = this@toProto.principalId.orEmpty()
    tenantId = this@toProto.tenantId.orEmpty()
    projectId = this@toProto.projectId.orEmpty()
    roles += this@toProto.roles
    environment = this@toProto.environment.orEmpty()
}

private fun GuardCheckResult.toHttpResponse() = GuardCheckHttpResponse(
    requestId = requestId,
    decision = decision.toDto(),
    riskScore = riskScore,
    violations = violationsList.map { violation ->
        ViolationEvidenceDto(
            policyId = violation.policyId,
            violationType = violation.violationType.toDto(),
            severity = violation.severity.toDto(),
            message = violation.message,
            startOffset = violation.startOffset,
            endOffset = violation.endOffset,
            detector = violation.detector,
        )
    },
    redactionResult = if (hasRedactionResult()) {
        RedactionResultDto(
            redacted = redactionResult.redacted,
            redactedContent = redactionResult.redactedContent,
            redactionCount = redactionResult.redactionCount,
            spans = redactionResult.spansList.map { span ->
                RedactionSpanDto(
                    startOffset = span.startOffset,
                    endOffset = span.endOffset,
                    replacement = span.replacement,
                    violationType = span.violationType.toDto(),
                )
            },
        )
    } else {
        null
    },
    policyRevision = policyRevision,
    coreLatencyMs = coreLatencyMs,
)

private fun PayloadTypeDto.toProto(): PayloadType = when (this) {
    PayloadTypeDto.TEXT -> PayloadType.PAYLOAD_TYPE_TEXT
    PayloadTypeDto.PROMPT -> PayloadType.PAYLOAD_TYPE_PROMPT
    PayloadTypeDto.RESPONSE -> PayloadType.PAYLOAD_TYPE_RESPONSE
    PayloadTypeDto.DATA_EGRESS -> PayloadType.PAYLOAD_TYPE_DATA_EGRESS
}

private fun ProviderTypeDto.toProto(): ProviderType = when (this) {
    ProviderTypeDto.OPENAI_COMPATIBLE -> ProviderType.PROVIDER_TYPE_OPENAI_COMPATIBLE
    ProviderTypeDto.ANTHROPIC -> ProviderType.PROVIDER_TYPE_ANTHROPIC
    ProviderTypeDto.GOOGLE -> ProviderType.PROVIDER_TYPE_GOOGLE
    ProviderTypeDto.INTERNAL -> ProviderType.PROVIDER_TYPE_INTERNAL
}

private fun DecisionType.toDto(): DecisionDto = when (this) {
    DecisionType.DECISION_TYPE_ALLOW -> DecisionDto.ALLOW
    DecisionType.DECISION_TYPE_REDACT -> DecisionDto.REDACT
    DecisionType.DECISION_TYPE_BLOCK -> DecisionDto.BLOCK
    DecisionType.DECISION_TYPE_UNSPECIFIED,
    DecisionType.UNRECOGNIZED,
    -> DecisionDto.BLOCK
}

private fun ViolationType.toDto(): ViolationTypeDto = when (this) {
    ViolationType.VIOLATION_TYPE_PROMPT_INJECTION -> ViolationTypeDto.PROMPT_INJECTION
    ViolationType.VIOLATION_TYPE_PII -> ViolationTypeDto.PII
    ViolationType.VIOLATION_TYPE_SECRET -> ViolationTypeDto.SECRET
    ViolationType.VIOLATION_TYPE_POLICY -> ViolationTypeDto.POLICY
    ViolationType.VIOLATION_TYPE_UNSPECIFIED,
    ViolationType.UNRECOGNIZED,
    -> ViolationTypeDto.UNSPECIFIED
}

private fun Severity.toDto(): SeverityDto = when (this) {
    Severity.SEVERITY_LOW -> SeverityDto.LOW
    Severity.SEVERITY_MEDIUM -> SeverityDto.MEDIUM
    Severity.SEVERITY_HIGH -> SeverityDto.HIGH
    Severity.SEVERITY_CRITICAL -> SeverityDto.CRITICAL
    Severity.SEVERITY_UNSPECIFIED,
    Severity.UNRECOGNIZED,
    -> SeverityDto.UNSPECIFIED
}
