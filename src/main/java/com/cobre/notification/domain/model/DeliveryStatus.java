package com.cobre.notification.domain.model;

public enum DeliveryStatus {
	/**
	 * Claimed for processing but not yet resolved — see
	 * {@link com.cobre.notification.domain.port.out.NotificationRecordPort#tryClaim}.
	 * A row stuck here past a timeout means the process died mid-delivery; a
	 * scheduled sweep flips it to {@link #FAILED} so it becomes replayable
	 * again instead of staying invisible.
	 */
	PROCESSING,
	DELIVERED,
	DUPLICATE,
	NOT_SUBSCRIBED,
	FAILED,
	/** Skipped without calling the webhook: its circuit breaker is open. */
	CIRCUIT_OPEN
}
