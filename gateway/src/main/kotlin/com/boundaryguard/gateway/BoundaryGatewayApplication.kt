package com.boundaryguard.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BoundaryGatewayApplication

fun main(args: Array<String>) {
    runApplication<BoundaryGatewayApplication>(*args)
}
