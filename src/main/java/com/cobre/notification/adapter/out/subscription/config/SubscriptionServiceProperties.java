package com.cobre.notification.adapter.out.subscription.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "subscription.service")
public record SubscriptionServiceProperties(
		String baseUrl,
		String path,
		Duration connectTimeout,
		Duration readTimeout) {
}
