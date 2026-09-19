package com.cobre.notification.adapter.in.web;

import java.util.List;

import com.cobre.notification.adapter.in.web.dto.NotificationEventDetailResponse;
import com.cobre.notification.adapter.in.web.dto.NotificationEventPageResponse;
import com.cobre.notification.adapter.in.web.dto.NotificationEventRequest;
import com.cobre.notification.adapter.in.web.dto.NotificationEventResponse;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.model.NotificationEventRecord;
import com.cobre.notification.domain.model.PagedResult;

final class NotificationEventWebMapper {

	private NotificationEventWebMapper() {
	}

	static NotificationEvent toDomain(NotificationEventRequest request) {
		return new NotificationEvent(
				request.eventId(),
				request.eventType(),
				request.content(),
				request.deliveryDate(),
				request.clientId());
	}

	static NotificationEventResponse toResponse(DeliveryResult result) {
		return new NotificationEventResponse(
				result.eventId(),
				result.status().name(),
				result.providerReference());
	}

	static NotificationEventDetailResponse toDetailResponse(NotificationEventRecord record) {
		return new NotificationEventDetailResponse(
				record.notificationEventId(),
				record.eventId(),
				record.eventType(),
				record.content(),
				record.clientId(),
				record.eventDeliveryDate(),
				record.deliveryStatus().name(),
				record.providerReference(),
				record.processedAt());
	}

	static NotificationEventPageResponse toPageResponse(PagedResult<NotificationEventRecord> page) {
		List<NotificationEventDetailResponse> items = page.items().stream()
				.map(NotificationEventWebMapper::toDetailResponse)
				.toList();
		return new NotificationEventPageResponse(items, page.totalElements(), page.limit(), page.offset());
	}
}
