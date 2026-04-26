package com.boundaryguard.gateway.coreclient

import com.boundaryguard.contract.guard.v1.CoreHealthCheckRequest
import com.boundaryguard.contract.guard.v1.CoreHealthCheckResult
import com.boundaryguard.contract.guard.v1.GuardCheckRequest
import com.boundaryguard.contract.guard.v1.GuardCheckResult
import com.boundaryguard.contract.guard.v1.GuardCoreServiceGrpcKt
import io.grpc.Status

class FakeGuardCoreService(
    private val checkHandler: suspend (GuardCheckRequest) -> GuardCheckResult,
    private val healthHandler: suspend (CoreHealthCheckRequest) -> CoreHealthCheckResult,
) : GuardCoreServiceGrpcKt.GuardCoreServiceCoroutineImplBase() {
    override suspend fun check(request: GuardCheckRequest): GuardCheckResult = checkHandler(request)

    override suspend fun health(request: CoreHealthCheckRequest): CoreHealthCheckResult = healthHandler(request)

    companion object {
        fun unavailable(): FakeGuardCoreService = FakeGuardCoreService(
            checkHandler = { throw Status.UNAVAILABLE.withDescription("core unavailable").asException() },
            healthHandler = { throw Status.UNAVAILABLE.withDescription("core unavailable").asException() },
        )
    }
}
