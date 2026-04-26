package com.boundaryguard.gateway.monitoring

import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Controller
class GuardMonitoringController(
    private val monitoringService: GuardEventMonitoringService,
    private val sseBroadcaster: GuardEventSseBroadcaster,
) {
    @GetMapping("/monitoring")
    fun dashboard(model: Model): String {
        model.addAttribute("dashboard", monitoringService.dashboard())
        return "monitoring/dashboard"
    }

    @GetMapping("/monitoring/events/{eventId}")
    fun eventDetail(
        @PathVariable eventId: String,
        model: Model,
    ): String {
        val event = monitoringService.detail(eventId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Guard event not found")
        model.addAttribute("event", event)
        return "monitoring/event-detail"
    }

    @GetMapping("/monitoring/events/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun eventStream(): SseEmitter = sseBroadcaster.subscribe()
}
