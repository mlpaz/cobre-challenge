package com.cobre.notification.domain.model;

public enum DeliveryStatus {
	DELIVERED,
	DUPLICATE,
	NOT_SUBSCRIBED,
	FAILED,
	/** Skipped without calling the webhook: its circuit breaker is open. */
	CIRCUIT_OPEN
}
