package com.cobre.notification.domain.exception;

import java.util.UUID;

public class NotificationEventNotFoundException extends RuntimeException {

	public NotificationEventNotFoundException(UUID notificationEventId) {
		super("Notification event not found: " + notificationEventId);
	}
}
