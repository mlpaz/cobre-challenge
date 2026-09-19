package com.cobre.notification.adapter.in.web.ratelimit.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param general applies to every request under /notification_events and /subscriptions
 * @param strict  overrides {@code general} for POST /subscriptions and POST
 *                /notification_events/{id}/replay — the highest-risk (see A01
 *                in the README's Seguridad section) and most expensive
 *                (triggers an outbound call) operations.
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(Tier general, Tier strict) {

	public record Tier(int capacity, int refillTokens, Duration refillPeriod) {
	}
}
