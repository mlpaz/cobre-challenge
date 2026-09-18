package com.cobre.notification.adapter.in.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlingConfig {

	/**
	 * Bounded retries for genuinely unexpected failures (e.g. a malformed
	 * message). Business outcomes (no subscription, delivery definitely
	 * failed) are already handled inside {@link NotificationEventKafkaListener}
	 * and never reach this handler.
	 */
	@Bean
	public DefaultErrorHandler kafkaErrorHandler() {
		return new DefaultErrorHandler(new FixedBackOff(1000L, 2L));
	}
}
