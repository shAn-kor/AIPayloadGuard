package com.boundaryguard.gateway.coreclient

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "boundary.guard.core")
data class RustCoreClientProperties(
    val target: String = "127.0.0.1:50051",
    val timeout: Duration = Duration.ofSeconds(2),
    val fallbackMode: CoreFallbackMode = CoreFallbackMode.FAIL_CLOSED,
)

enum class CoreFallbackMode {
    FAIL_CLOSED,
    FAIL_OPEN,
}
