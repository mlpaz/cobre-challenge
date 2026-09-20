package com.cobre.notification.domain.model;

/**
 * Read model for a subscription row, as exposed by {@code GET /subscriptions}
 * — unlike {@link Subscription} (the write model used by subscribe/update),
 * this also carries the webhook's circuit breaker state (see
 * "Score y circuit breaker por webhook" in the README).
 *
 * @param open whether this webhook's circuit is currently {@code OPEN}
 *             (blocking automatic delivery) — {@code false} while
 *             {@code CLOSED} or {@code HALF_OPEN}, since a {@code HALF_OPEN}
 *             circuit still lets trial deliveries through.
 */
public record SubscriptionStatus(
		String userId,
		String eventType,
		String webHookUrl,
		int successScore,
		boolean open) {
}
