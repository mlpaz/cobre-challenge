package com.cobre.notification.adapter.out.provider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cobre.notification.adapter.out.provider.config.NotificationProviderProperties;
import com.cobre.notification.domain.port.out.MetricsPort;

@Configuration
public class NotificationProviderClientConfig {

	@Bean
	public NotificationProviderHttpClient notificationProviderHttpClient(NotificationProviderProperties properties,
			MetricsPort metricsPort) {
		return new NotificationProviderHttpClient(properties, metricsPort);
	}
}
