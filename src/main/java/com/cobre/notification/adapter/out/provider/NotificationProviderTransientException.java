package com.cobre.notification.adapter.out.provider;

/**
 * Signals a transient failure talking to the Notification Provider (5xx, timeout,
 * connection failure) that is safe to retry.
 */
public class NotificationProviderTransientException extends RuntimeException {

	public NotificationProviderTransientException(String message, Throwable cause) {
		super(message, cause);
	}
}
