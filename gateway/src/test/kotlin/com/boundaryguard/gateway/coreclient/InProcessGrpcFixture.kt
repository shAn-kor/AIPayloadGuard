package com.boundaryguard.gateway.coreclient

import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import java.util.UUID

class InProcessGrpcFixture(
    service: BindableService,
) : AutoCloseable {
    private val serverName: String = "test-core-${UUID.randomUUID()}"
    private val server: Server = InProcessServerBuilder
        .forName(serverName)
        .directExecutor()
        .addService(service)
        .build()
        .start()

    val channel: ManagedChannel = InProcessChannelBuilder
        .forName(serverName)
        .directExecutor()
        .build()

    override fun close() {
        channel.shutdownNow()
        server.shutdownNow()
    }
}
