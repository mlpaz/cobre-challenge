package com.cobre.notification.adapter.in.web;

import com.cobre.notification.adapter.in.web.dto.NotificationEventRequest;
import com.cobre.notification.adapter.in.web.dto.NotificationEventResponse;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.NotificationEvent;

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
}
