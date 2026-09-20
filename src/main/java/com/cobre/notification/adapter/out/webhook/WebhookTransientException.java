package com.cobre.notification.adapter.out.webhook;

/**
 * Signals a transient failure calling the client's webhook (5xx, timeout,
 * connection failure) that is safe to retry.
 */
public class WebhookTransientException extends RuntimeException {

	public WebhookTransientException(String message, Throwable cause) {
		super(message, cause);
	}
}
