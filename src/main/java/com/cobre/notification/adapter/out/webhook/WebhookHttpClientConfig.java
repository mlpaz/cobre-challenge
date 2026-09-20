package com.cobre.notification.adapter.out.webhook;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cobre.notification.adapter.out.webhook.config.WebhookHttpProperties;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.WebhookCircuitBreakerPort;

@Configuration
public class WebhookHttpClientConfig {

	@Bean
	public WebhookHttpClient webhookHttpClient(WebhookHttpProperties properties, MetricsPort metricsPort,
			WebhookCircuitBreakerPort webhookCircuitBreakerPort) {
		return new WebhookHttpClient(properties, metricsPort, webhookCircuitBreakerPort);
	}
}
