package com.cobre.notification.domain.exception;

/**
 * Raised when the {@code x-user-id} header does not match the client_id that
 * owns the requested notification event.
 */
public class NotificationEventAccessDeniedException extends RuntimeException {

	public NotificationEventAccessDeniedException(String message) {
		super(message);
	}
}
