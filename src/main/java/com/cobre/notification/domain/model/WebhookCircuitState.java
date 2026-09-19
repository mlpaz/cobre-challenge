package com.cobre.notification.domain.model;

/** State of a single webhook's (per-subscription) circuit breaker. */
public enum WebhookCircuitState {
	CLOSED,
	OPEN,
	HALF_OPEN
}
