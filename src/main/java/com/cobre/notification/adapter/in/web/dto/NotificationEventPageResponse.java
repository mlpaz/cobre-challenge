package com.cobre.notification.adapter.in.web.dto;

import java.util.List;

public record NotificationEventPageResponse(
		List<NotificationEventDetailResponse> items,
		long totalElements,
		int limit,
		int offset) {
}
