package com.cobre.notification.adapter.out.webhook.dto;

import java.time.Instant;

public record WebhookNotificationRequest(
		String eventId,
		String eventType,
		String content,
		Instant deliveryDate,
		String clientId) {
}
