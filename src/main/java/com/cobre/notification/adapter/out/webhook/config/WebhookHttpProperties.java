package com.cobre.notification.adapter.out.webhook.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.webhook.delivery")
public record WebhookHttpProperties(
		Duration connectTimeout,
		Duration readTimeout,
		Retry retry) {

	public record Retry(
			int maxAttempts,
			Duration waitDuration,
			double exponentialBackoffMultiplier) {
	}
}
