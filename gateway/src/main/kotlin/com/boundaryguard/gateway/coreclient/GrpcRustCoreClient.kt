package com.boundaryguard.gateway.coreclient

import com.boundaryguard.contract.guard.v1.CoreHealthCheckResult
import com.boundaryguard.contract.guard.v1.GuardCheckRequest
import com.boundaryguard.contract.guard.v1.GuardCheckResult
import com.boundaryguard.contract.guard.v1.GuardCoreServiceGrpcKt
import com.boundaryguard.contract.guard.v1.coreHealthCheckRequest
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.springframework.stereotype.Component

@Component
class GrpcRustCoreClient(
    private val stub: GuardCoreServiceGrpcKt.GuardCoreServiceCoroutineStub,
    private val properties: RustCoreClientProperties,
) : RustCoreClient {
    override suspend fun check(request: GuardCheckRequest): GuardCheckResult = callCore("Check") {
        stub.check(request)
    }

    override suspend fun health(requestId: String): CoreHealthCheckResult = callCore("Health") {
        stub.health(coreHealthCheckRequest {
            this.requestId = requestId
        })
    }

    private suspend fun <T> callCore(operation: String, call: suspend () -> T): T = try {
        withTimeout(properties.timeout.toMillis()) {
            call()
        }
    } catch (exception: TimeoutCancellationException) {
        throw RustCoreUnavailableException("Rust Core $operation timed out after ${properties.timeout.toMillis()}ms", exception)
    } catch (exception: StatusException) {
        throw RustCoreUnavailableException("Rust Core $operation failed with status ${exception.status.code}", exception)
    } catch (exception: StatusRuntimeException) {
        throw RustCoreUnavailableException("Rust Core $operation failed with status ${exception.status.code}", exception)
    }
}
