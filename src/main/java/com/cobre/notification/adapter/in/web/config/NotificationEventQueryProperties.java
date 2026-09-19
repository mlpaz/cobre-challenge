package com.cobre.notification.adapter.in.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.events.query")
public record NotificationEventQueryProperties(int defaultLimit, int maxLimit, int maxOffset) {
}
