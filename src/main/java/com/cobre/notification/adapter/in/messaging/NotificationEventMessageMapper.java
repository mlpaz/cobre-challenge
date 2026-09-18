package com.cobre.notification.adapter.in.messaging;

import com.cobre.notification.adapter.in.messaging.dto.NotificationEventMessage;
import com.cobre.notification.domain.model.NotificationEvent;

final class NotificationEventMessageMapper {

	private NotificationEventMessageMapper() {
	}

	static NotificationEvent toDomain(NotificationEventMessage message) {
		return new NotificationEvent(
				message.eventId(),
				message.eventType(),
				message.content(),
				message.deliveryDate(),
				message.clientId());
	}
}
