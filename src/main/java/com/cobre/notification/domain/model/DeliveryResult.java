package com.cobre.notification.domain.model;

public record DeliveryResult(
		String eventId,
		DeliveryStatus status,
		String webhookResponse) {
}
