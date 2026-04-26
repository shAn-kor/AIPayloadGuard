package com.boundaryguard.gateway.audit

import com.boundaryguard.gateway.api.GuardCheckHttpRequest
import com.boundaryguard.gateway.api.GuardCheckHttpResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GuardEventRecorder(
    private val guardEventFactory: GuardEventFactory,
    private val guardEventSink: GuardEventSink,
) {
    fun record(request: GuardCheckHttpRequest, response: GuardCheckHttpResponse) {
        val event = guardEventFactory.create(request, response)
        runCatching { guardEventSink.publish(event) }
            .onSuccess {
                log.info(
                    "guard_event_recorded eventId={} requestId={} decision={} riskScore={} highRisk={}",
                    event.eventId,
                    event.requestId,
                    event.decision,
                    event.riskScore,
                    event.highRisk,
                )
            }
            .onFailure { error ->
                log.error(
                    "guard_event_record_failed requestId={} decision={} riskScore={}",
                    event.requestId,
                    event.decision,
                    event.riskScore,
                    error,
                )
            }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GuardEventRecorder::class.java)
    }
}
