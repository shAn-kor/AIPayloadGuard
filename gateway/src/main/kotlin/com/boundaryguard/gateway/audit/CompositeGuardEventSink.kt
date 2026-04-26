package com.boundaryguard.gateway.audit

class CompositeGuardEventSink(
    private val sinks: List<GuardEventSink>,
) : GuardEventSink {
    override fun publish(event: GuardEvent) {
        sinks.forEach { sink -> sink.publish(event) }
    }
}
