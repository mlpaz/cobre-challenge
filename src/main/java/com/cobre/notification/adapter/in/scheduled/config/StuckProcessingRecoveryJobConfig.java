package com.cobre.notification.adapter.in.scheduled.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cobre.notification.adapter.in.scheduled.StuckProcessingRecoveryJob;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.NotificationRecordPort;
import com.cobre.notification.domain.port.out.SubscriptionPort;

@Configuration
public class StuckProcessingRecoveryJobConfig {

	@Bean
	public StuckProcessingRecoveryJob stuckProcessingRecoveryJob(NotificationRecordPort notificationRecordPort,
			SubscriptionPort subscriptionPort, MetricsPort metricsPort, StuckProcessingProperties properties) {
		return new StuckProcessingRecoveryJob(notificationRecordPort, subscriptionPort, metricsPort, properties);
	}
}
