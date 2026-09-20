package com.cobre.notification.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationEventDetailResponse(
		UUID notificationEventId,
		String eventId,
		String eventType,
		String content,
		String clientId,
		Instant deliveryDate,
		String deliveryStatus,
		String webhookResponse,
		Instant processedAt) {
}
