package com.cobre.notification.domain.exception;

/**
 * The event has no active subscription for its (client, event type) pair:
 * either the client never subscribed to this event type, or the event does
 * not actually belong to the client it claims. Not retryable.
 */
public class SubscriptionNotConfirmedException extends RuntimeException {

	public SubscriptionNotConfirmedException(String message) {
		super(message);
	}
}
