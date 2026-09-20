package com.cobre.notification.adapter.out.webhook;

/**
 * Signals that the client's webhook rejected the request (4xx). Not
 * retryable: the payload itself is the problem, retrying would fail again.
 */
public class WebhookRejectedException extends RuntimeException {

	public WebhookRejectedException(String message, Throwable cause) {
		super(message, cause);
	}
}
