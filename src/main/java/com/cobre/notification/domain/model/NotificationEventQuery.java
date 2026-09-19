package com.cobre.notification.domain.model;

import java.time.Instant;

/**
 * Search criteria for listing a client's notification events, ordered by
 * event delivery date. {@code deliveryStatus}, {@code createdFrom} and
 * {@code createdTo} are optional filters; {@code null} means unfiltered.
 */
public record NotificationEventQuery(
		String clientId,
		DeliveryStatus deliveryStatus,
		Instant createdFrom,
		Instant createdTo,
		int limit,
		int offset) {
}
