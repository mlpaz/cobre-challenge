package com.cobre.notification.adapter.in.messaging.dto;

import java.time.Instant;

public record NotificationEventMessage(
		String eventId,
		String eventType,
		String content,
		Instant deliveryDate,
		String clientId) {
}
