package com.cobre.notification.domain.exception;

public class SubscriptionNotFoundException extends RuntimeException {

	public SubscriptionNotFoundException(String userId, String eventType) {
		super("No subscription found for user " + userId + " and event type " + eventType);
	}
}
