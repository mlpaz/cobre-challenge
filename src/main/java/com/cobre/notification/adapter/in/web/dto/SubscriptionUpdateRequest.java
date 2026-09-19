package com.cobre.notification.adapter.in.web.dto;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;

public record SubscriptionUpdateRequest(
		@NotBlank @URL String webHookUrl) {
}
