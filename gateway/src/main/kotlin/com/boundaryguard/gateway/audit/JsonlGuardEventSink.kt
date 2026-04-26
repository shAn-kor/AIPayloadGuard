package com.boundaryguard.gateway.audit

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class JsonlGuardEventSink(
    private val eventLogPath: Path,
    private val objectMapper: ObjectMapper,
) : GuardEventSink {
    override fun publish(event: GuardEvent) {
        Files.createDirectories(eventLogPath.parent)
        val jsonLine = objectMapper.writeValueAsString(event) + System.lineSeparator()
        Files.writeString(
            eventLogPath,
            jsonLine,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
    }
}
