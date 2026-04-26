package com.boundaryguard.gateway.coreclient

import com.boundaryguard.contract.guard.v1.GuardCoreServiceGrpcKt
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(RustCoreClientProperties::class)
class RustCoreClientConfiguration {
    @Bean(destroyMethod = "shutdown")
    fun rustCoreManagedChannel(properties: RustCoreClientProperties): ManagedChannel =
        ManagedChannelBuilder.forTarget(properties.target)
            .usePlaintext()
            .build()

    @Bean
    fun guardCoreServiceCoroutineStub(channel: ManagedChannel): GuardCoreServiceGrpcKt.GuardCoreServiceCoroutineStub =
        GuardCoreServiceGrpcKt.GuardCoreServiceCoroutineStub(channel)
}
