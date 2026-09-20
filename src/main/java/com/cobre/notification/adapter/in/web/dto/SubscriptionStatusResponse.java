package com.cobre.notification.adapter.in.web.dto;

public record SubscriptionStatusResponse(
		String userId,
		String eventType,
		String webHookUrl,
		int successScore,
		boolean open) {
}
