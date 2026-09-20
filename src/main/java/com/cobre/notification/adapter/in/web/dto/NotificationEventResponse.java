package com.cobre.notification.adapter.in.web.dto;

public record NotificationEventResponse(
		String eventId,
		String deliveryStatus,
		String webhookResponse) {
}
