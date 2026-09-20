package com.cobre.notification.adapter.out.circuitbreaker.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.webhook.circuit-breaker")
public record WebhookCircuitBreakerProperties(
		int minSuccessScore,
		int minimumNumberOfCalls,
		Duration waitDurationInOpenState,
		int permittedNumberOfCallsInHalfOpenState,
		double scoreSmoothingFactor) {
}
