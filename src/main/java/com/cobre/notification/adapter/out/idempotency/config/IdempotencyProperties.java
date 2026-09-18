package com.cobre.notification.adapter.out.idempotency.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "idempotency")
public record IdempotencyProperties(Duration ttl) {
}
