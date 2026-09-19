package com.cobre.notification.adapter.out.provider.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.provider")
public record NotificationProviderProperties(
		String baseUrl,
		String path,
		Duration connectTimeout,
		Duration readTimeout,
		Retry retry) {

	public record Retry(
			int maxAttempts,
			Duration waitDuration,
			double exponentialBackoffMultiplier) {
	}
}
