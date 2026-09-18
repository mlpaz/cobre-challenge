package com.cobre.notification.adapter.in.web.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationEventRequest(
		@NotBlank String eventId,
		@NotBlank String eventType,
		@NotBlank String content,
		@NotNull Instant deliveryDate,
		@NotBlank String clientId) {
}
