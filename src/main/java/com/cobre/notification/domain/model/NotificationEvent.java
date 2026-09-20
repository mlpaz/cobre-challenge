package com.cobre.notification.domain.model;

import java.time.Instant;

import com.cobre.notification.domain.exception.InvalidEventTypeException;

public record NotificationEvent(
		String eventId,
		String eventType,
		String content,
		Instant deliveryDate,
		String clientId) {

	public NotificationEvent {
		// Enforced once, here, so it applies the same way regardless of entry
		// point (POST /notification_events, the Kafka listener, or replaying a
		// previously stored event) instead of being a web-layer-only check.
		if (!EventType.isValid(eventType)) {
			throw new InvalidEventTypeException(eventType);
		}
	}
}
