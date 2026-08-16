package com.expensemanagement.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PreDestroy;

/**
 * A single long-lived gRPC channel to the anomaly-scoring service, shared
 * across requests. Plaintext (no TLS) because this traffic never leaves the
 * private network the backend and the scoring service share -- see the
 * Docker Compose network for how that's wired locally.
 */
@Configuration
public class GrpcClientConfig {

    private ManagedChannel channel;

    @Bean
    public ManagedChannel anomalyScoringChannel(@Value("${app.anomaly-scoring.grpc-target}") String target) {
        this.channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .keepAliveTime(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        return channel;
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            channel.shutdown();
        }
    }
}
