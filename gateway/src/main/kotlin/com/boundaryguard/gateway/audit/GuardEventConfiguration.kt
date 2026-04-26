package com.boundaryguard.gateway.audit

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GuardEventConfiguration {
    @Bean
    fun guardEventClock(): Clock = defaultClock()
}
