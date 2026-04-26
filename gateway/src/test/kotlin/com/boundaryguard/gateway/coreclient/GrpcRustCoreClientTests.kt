package com.boundaryguard.gateway.coreclient

import com.boundaryguard.contract.guard.v1.CoreHealthCheckResult
import com.boundaryguard.contract.guard.v1.DecisionType
import com.boundaryguard.contract.guard.v1.GuardCheckResult
import com.boundaryguard.contract.guard.v1.GuardCoreServiceGrpcKt
import com.boundaryguard.contract.guard.v1.PayloadType
import com.boundaryguard.contract.guard.v1.coreHealthCheckResult
import com.boundaryguard.contract.guard.v1.guardCheckRequest
import com.boundaryguard.contract.guard.v1.guardCheckResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class GrpcRustCoreClientTests {
    @Test
    fun `check delegates to Rust Core service`() = runBlocking {
        InProcessGrpcFixture(FakeGuardCoreService(
            checkHandler = { request ->
                guardCheckResult {
                    requestId = request.requestId
                    decision = DecisionType.DECISION_TYPE_ALLOW
                    riskScore = 0
                    policyRevision = "builtin-mvp"
                    coreLatencyMs = 1
                }
            },
            healthHandler = { CoreHealthCheckResult.getDefaultInstance() },
        )).use { fixture ->
            val client = client(fixture)
            val result = client.check(guardCheckRequest {
                requestId = "req-1"
                payloadType = PayloadType.PAYLOAD_TYPE_PROMPT
                content = "hello"
            })

            assertEquals("req-1", result.requestId)
            assertEquals(DecisionType.DECISION_TYPE_ALLOW, result.decision)
        }
    }

    @Test
    fun `health delegates to Rust Core service`() = runBlocking {
        InProcessGrpcFixture(FakeGuardCoreService(
            checkHandler = { GuardCheckResult.getDefaultInstance() },
            healthHandler = { request ->
                coreHealthCheckResult {
                    requestId = request.requestId
                    ready = true
                    coreVersion = "0.1.0"
                    loadedPolicyRevision = "builtin-mvp"
                    enabledDetectors += listOf("prompt_injection", "pii", "secret")
                }
            },
        )).use { fixture ->
            val client = client(fixture)
            val result = client.health("health-1")

            assertEquals("health-1", result.requestId)
            assertEquals(true, result.ready)
            assertEquals("0.1.0", result.coreVersion)
        }
    }

    @Test
    fun `grpc errors are mapped to RustCoreUnavailableException`() = runBlocking {
        InProcessGrpcFixture(FakeGuardCoreService.unavailable()).use { fixture ->
            val client = client(fixture)

            assertThrows<RustCoreUnavailableException> {
                client.check(guardCheckRequest {
                    requestId = "req-1"
                    payloadType = PayloadType.PAYLOAD_TYPE_PROMPT
                    content = "hello"
                })
            }
        }
    }

    private fun client(fixture: InProcessGrpcFixture): GrpcRustCoreClient = GrpcRustCoreClient(
        stub = GuardCoreServiceGrpcKt.GuardCoreServiceCoroutineStub(fixture.channel),
        properties = RustCoreClientProperties(timeout = Duration.ofSeconds(30)),
    )
}
