package com.boundaryguard.gateway.audit

interface GuardEventSink {
    fun publish(event: GuardEvent)
}
