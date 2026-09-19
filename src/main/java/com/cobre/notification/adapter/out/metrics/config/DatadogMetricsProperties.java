package com.cobre.notification.adapter.out.metrics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "datadog.metrics")
public record DatadogMetricsProperties(
		@DefaultValue("localhost") String host,
		@DefaultValue("8125") int port,
		@DefaultValue("notification") String prefix,
		String[] constantTags) {
}
