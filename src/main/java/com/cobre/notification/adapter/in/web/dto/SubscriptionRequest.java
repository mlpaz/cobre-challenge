package com.cobre.notification.adapter.in.web.dto;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;

public record SubscriptionRequest(
		@NotBlank String userId,
		@NotBlank String eventType,
		@NotBlank @URL String webHookUrl) {
}
