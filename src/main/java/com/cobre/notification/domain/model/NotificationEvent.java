package com.cobre.notification.domain.model;

import java.time.Instant;

public record NotificationEvent(
		String eventId,
		String eventType,
		String content,
		Instant deliveryDate,
		String clientId) {
}
