package com.boundaryguard.gateway.audit

import java.util.ArrayDeque
import org.springframework.stereotype.Component

private const val DEFAULT_RECENT_EVENT_CAPACITY = 500

@Component
class InMemoryRecentGuardEventBuffer : GuardEventSink {
    private val events = ArrayDeque<GuardEvent>()

    override fun publish(event: GuardEvent) {
        synchronized(events) {
            if (events.size >= DEFAULT_RECENT_EVENT_CAPACITY) {
                events.removeFirst()
            }
            events.addLast(event)
        }
    }

    fun publishIfAbsent(event: GuardEvent) {
        synchronized(events) {
            if (events.any { existing -> existing.eventId == event.eventId }) {
                return
            }
            if (events.size >= DEFAULT_RECENT_EVENT_CAPACITY) {
                events.removeFirst()
            }
            events.addLast(event)
        }
    }

    fun recent(limit: Int = DEFAULT_RECENT_EVENT_CAPACITY): List<GuardEvent> {
        require(limit >= 0) { "limit must not be negative" }
        return synchronized(events) {
            events.toList().takeLast(limit)
        }
    }

    fun clear() {
        synchronized(events) {
            events.clear()
        }
    }
}
