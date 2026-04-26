package com.boundaryguard.gateway.api

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class GuardCheckHttpRequest(
    @field:NotBlank
    val requestId: String,

    @field:NotNull
    val payloadType: PayloadTypeDto,

    @field:NotBlank
    val content: String,

    @field:Valid
    val providerMetadata: ProviderMetadataDto? = null,

    @field:Valid
    val principalContext: PrincipalContextDto? = null,

    val policyRevision: String? = null,

    val metadata: Map<String, String>? = null,
)

data class ProviderMetadataDto(
    @field:NotNull
    val providerType: ProviderTypeDto,

    val providerName: String? = null,
    val model: String? = null,
    val endpoint: String? = null,
)

data class PrincipalContextDto(
    val principalId: String? = null,
    val tenantId: String? = null,
    val projectId: String? = null,
    val roles: List<String> = emptyList(),
    val environment: String? = null,
)

data class GuardCheckHttpResponse(
    val requestId: String,
    val decision: DecisionDto,

    @field:Min(0)
    @field:Max(100)
    val riskScore: Int,

    val violations: List<ViolationEvidenceDto>,
    val redactionResult: RedactionResultDto?,
    val policyRevision: String,
    val coreLatencyMs: Long,
)

data class ViolationEvidenceDto(
    val policyId: String,
    val violationType: ViolationTypeDto,
    val severity: SeverityDto,
    val message: String,
    val startOffset: Int,
    val endOffset: Int,
    val detector: String,
)

data class RedactionResultDto(
    val redacted: Boolean,
    val redactedContent: String,
    val redactionCount: Int,
    val spans: List<RedactionSpanDto>,
)

data class RedactionSpanDto(
    val startOffset: Int,
    val endOffset: Int,
    val replacement: String,
    val violationType: ViolationTypeDto,
)

enum class PayloadTypeDto {
    TEXT,
    PROMPT,
    RESPONSE,
    DATA_EGRESS,
}

enum class ProviderTypeDto {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GOOGLE,
    INTERNAL,
}

enum class DecisionDto {
    ALLOW,
    REDACT,
    BLOCK,
}

enum class ViolationTypeDto {
    PROMPT_INJECTION,
    PII,
    SECRET,
    POLICY,
    UNSPECIFIED,
}

enum class SeverityDto {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    UNSPECIFIED,
}
