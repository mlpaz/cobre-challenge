package com.cobre.notification.adapter.out.subscription;

/**
 * The subscription registry could not be reached or answered with an
 * unexpected error while confirming a subscription.
 */
public class SubscriptionServiceUnavailableException extends RuntimeException {

	public SubscriptionServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
