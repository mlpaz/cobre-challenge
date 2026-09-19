package com.cobre.notification.domain.exception;

import java.util.UUID;

/**
 * Raised when the {@code x-user-id} header does not match the client_id that
 * owns the requested notification event. Carries the structured context (not
 * just a message) so the web layer can log and alert on it — see A09 in the
 * README's Seguridad section.
 */
public class NotificationEventAccessDeniedException extends RuntimeException {

	private final UUID notificationEventId;
	private final String requestedByUserId;

	public NotificationEventAccessDeniedException(UUID notificationEventId, String requestedByUserId) {
		super("The notification event does not belong to the requesting user");
		this.notificationEventId = notificationEventId;
		this.requestedByUserId = requestedByUserId;
	}

	public UUID notificationEventId() {
		return notificationEventId;
	}

	public String requestedByUserId() {
		return requestedByUserId;
	}
}
