package com.cobre.notification.adapter.out.metrics;

import org.springframework.stereotype.Component;

import com.cobre.notification.adapter.out.metrics.config.DatadogMetricsProperties;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.timgroup.statsd.NonBlockingStatsDClientBuilder;
import com.timgroup.statsd.StatsDClient;

import jakarta.annotation.PreDestroy;

/**
 * Ships metrics to a local Datadog Agent over DogStatsD (UDP, fire-and-forget:
 * a lost metric never affects the delivery flow it's measuring).
 */
@Component
public class DatadogMetricsAdapter implements MetricsPort {

	private final StatsDClient client;

	public DatadogMetricsAdapter(DatadogMetricsProperties properties) {
		String[] constantTags = properties.constantTags() == null ? new String[0] : properties.constantTags();
		this.client = new NonBlockingStatsDClientBuilder()
				.prefix(properties.prefix())
				.hostname(properties.host())
				.port(properties.port())
				.constantTags(constantTags)
				.build();
	}

	@Override
	public void increment(String metric, String... tags) {
		client.increment(metric, tags);
	}

	@PreDestroy
	public void close() {
		client.close();
	}
}
