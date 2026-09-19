package com.cobre.notification.domain.model;

public enum DeliveryStatus {
	DELIVERED,
	DUPLICATE,
	NOT_SUBSCRIBED,
	FAILED,
	/** Skipped without contacting the provider: the webhook's circuit breaker is open. */
	CIRCUIT_OPEN
}
