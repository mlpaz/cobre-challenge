package com.cobre.notification.adapter.out.provider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cobre.notification.adapter.out.provider.config.NotificationProviderProperties;

@Configuration
public class NotificationProviderClientConfig {

	@Bean
	public NotificationProviderHttpClient notificationProviderHttpClient(NotificationProviderProperties properties) {
		return new NotificationProviderHttpClient(properties);
	}
}
