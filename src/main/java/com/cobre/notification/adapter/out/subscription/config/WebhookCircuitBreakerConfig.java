package com.cobre.notification.adapter.out.subscription.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cobre.notification.adapter.out.subscription.SubscriptionJpaRepository;
import com.cobre.notification.adapter.out.subscription.WebhookCircuitBreakerJpaAdapter;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.WebhookCircuitBreakerPort;

@Configuration
public class WebhookCircuitBreakerConfig {

	@Bean
	public WebhookCircuitBreakerPort webhookCircuitBreakerPort(SubscriptionJpaRepository repository,
			WebhookCircuitBreakerProperties properties, MetricsPort metricsPort) {
		return new WebhookCircuitBreakerJpaAdapter(repository, properties, metricsPort);
	}
}
