package com.cobre.notification.adapter.in.scheduled.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.events.stuck-processing")
public record StuckProcessingProperties(Duration threshold, Duration sweepInterval) {
}
