package com.boundaryguard.gateway.audit

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Clock
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(GuardEventSinkProperties::class)
class GuardEventConfiguration {
    @Bean
    fun guardEventClock(): Clock = defaultClock()

    @Bean
    fun jsonlGuardEventSink(
        properties: GuardEventSinkProperties,
        objectMapper: ObjectMapper,
    ): JsonlGuardEventSink = JsonlGuardEventSink(properties.jsonlPath, objectMapper)

    @Bean
    fun guardEventSink(
        jsonlGuardEventSink: JsonlGuardEventSink,
        recentGuardEventBuffer: InMemoryRecentGuardEventBuffer,
    ): GuardEventSink = CompositeGuardEventSink(
        listOf(jsonlGuardEventSink, recentGuardEventBuffer),
    )
}
