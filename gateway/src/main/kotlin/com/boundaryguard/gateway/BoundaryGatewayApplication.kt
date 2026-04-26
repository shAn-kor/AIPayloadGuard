package com.boundaryguard.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class BoundaryGatewayApplication

fun main(args: Array<String>) {
    runApplication<BoundaryGatewayApplication>(*args)
}
