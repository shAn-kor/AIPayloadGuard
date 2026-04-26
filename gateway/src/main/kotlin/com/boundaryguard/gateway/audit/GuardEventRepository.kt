package com.boundaryguard.gateway.audit

interface GuardEventRepository {
    fun save(event: GuardEvent): GuardEvent
}
