package com.cobre.notification.adapter.out.provider;

/**
 * Signals that the Notification Provider rejected the request (4xx). Not
 * retryable: the payload itself is the problem, retrying would fail again.
 */
public class NotificationProviderRejectedException extends RuntimeException {

	public NotificationProviderRejectedException(String message, Throwable cause) {
		super(message, cause);
	}
}
