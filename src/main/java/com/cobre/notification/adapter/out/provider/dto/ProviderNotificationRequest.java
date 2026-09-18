package com.cobre.notification.adapter.out.provider.dto;

import java.time.Instant;

public record ProviderNotificationRequest(
		String eventId,
		String eventType,
		String content,
		Instant deliveryDate,
		String clientId) {
}
