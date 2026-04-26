package com.boundaryguard.gateway.application

import com.boundaryguard.gateway.api.GuardCheckHttpRequest
import com.boundaryguard.gateway.api.GuardCheckHttpResponse
import com.boundaryguard.gateway.audit.GuardEventRecorder
import com.boundaryguard.gateway.coreclient.RustCoreClient
import org.springframework.stereotype.Service

@Service
class GuardCheckApplicationService(
    private val rustCoreClient: RustCoreClient,
    private val guardCheckProtoMapper: GuardCheckProtoMapper,
    private val guardEventRecorder: GuardEventRecorder,
) {
    suspend fun check(request: GuardCheckHttpRequest): GuardCheckHttpResponse {
        val coreResult = rustCoreClient.check(guardCheckProtoMapper.toProto(request))
        val response = guardCheckProtoMapper.toHttpResponse(coreResult)
        guardEventRecorder.record(request, response)
        return response
    }
}
