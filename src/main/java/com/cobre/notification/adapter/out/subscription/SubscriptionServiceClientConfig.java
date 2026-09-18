package com.cobre.notification.adapter.out.subscription;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cobre.notification.adapter.out.subscription.config.SubscriptionServiceProperties;

@Configuration
public class SubscriptionServiceClientConfig {

	@Bean
	public SubscriptionHttpClient subscriptionHttpClient(SubscriptionServiceProperties properties) {
		return new SubscriptionHttpClient(properties);
	}
}
