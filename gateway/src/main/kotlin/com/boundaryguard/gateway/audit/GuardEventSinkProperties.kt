package com.boundaryguard.gateway.audit

import java.nio.file.Path
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "boundary.guard-events")
data class GuardEventSinkProperties(
    val jsonlPath: Path = Path.of("logs", "guard-events.jsonl"),
)
