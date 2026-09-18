package com.cobre.notification.domain.exception;

/**
 * The subscription registry could not be reached to confirm whether the
 * event should be delivered. We fail closed: an event is never delivered
 * without a confirmed subscription.
 */
public class SubscriptionCheckException extends RuntimeException {

	public SubscriptionCheckException(String message, Throwable cause) {
		super(message, cause);
	}
}
