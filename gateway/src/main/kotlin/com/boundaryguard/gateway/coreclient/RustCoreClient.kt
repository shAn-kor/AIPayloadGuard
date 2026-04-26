package com.boundaryguard.gateway.coreclient

import com.boundaryguard.contract.guard.v1.CoreHealthCheckResult
import com.boundaryguard.contract.guard.v1.GuardCheckRequest
import com.boundaryguard.contract.guard.v1.GuardCheckResult

interface RustCoreClient {
    suspend fun check(request: GuardCheckRequest): GuardCheckResult

    suspend fun health(requestId: String): CoreHealthCheckResult
}
