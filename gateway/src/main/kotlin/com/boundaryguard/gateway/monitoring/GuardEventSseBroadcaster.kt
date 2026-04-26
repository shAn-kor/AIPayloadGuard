package com.boundaryguard.gateway.monitoring

import com.boundaryguard.gateway.audit.GuardEvent
import com.boundaryguard.gateway.audit.GuardEventSink
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

private const val DEFAULT_SSE_TIMEOUT_MS = 60_000L

@Component
open class GuardEventSseBroadcaster : GuardEventSink {
    private val emitters = CopyOnWriteArrayList<SseEmitter>()

    open fun subscribe(): SseEmitter {
        val emitter = SseEmitter(DEFAULT_SSE_TIMEOUT_MS)
        emitters += emitter
        emitter.onCompletion { emitters -= emitter }
        emitter.onTimeout {
            emitters -= emitter
            emitter.complete()
        }
        emitter.onError {
            emitters -= emitter
            emitter.complete()
        }
        return emitter
    }

    override fun publish(event: GuardEvent) {
        if (!event.highRisk) {
            return
        }
        emit(event)
    }

    protected open fun emit(event: GuardEvent) {
        emitters.forEach { emitter -> send(emitter, event) }
    }

    private fun send(emitter: SseEmitter, event: GuardEvent) {
        runCatching {
            emitter.send(
                SseEmitter.event()
                    .name("guard-event")
                    .id(event.eventId)
                    .data(GuardEventSsePayload.from(event)),
            )
        }.onFailure { error ->
            emitters -= emitter
            if (error !is IOException) {
                log.debug("guard_event_sse_send_failed eventId={}", event.eventId, error)
            }
            emitter.completeWithError(error)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GuardEventSseBroadcaster::class.java)
    }
}

data class GuardEventSsePayload(
    val eventId: String,
    val requestId: String,
    val occurredAt: String,
    val decision: String,
    val riskScore: Int,
    val severity: String,
    val payloadType: String,
    val redactedSummary: String?,
    val violationCount: Int,
) {
    companion object {
        fun from(event: GuardEvent): GuardEventSsePayload = GuardEventSsePayload(
            eventId = event.eventId,
            requestId = event.requestId,
            occurredAt = event.occurredAt.toString(),
            decision = event.decision.name,
            riskScore = event.riskScore,
            severity = event.severity.name,
            payloadType = event.payloadType.name,
            redactedSummary = event.redactedSummary,
            violationCount = event.violations.size,
        )
    }
}
