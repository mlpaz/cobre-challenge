package com.cobre.notification.adapter.in.web.ratelimit;

import java.time.Duration;

/**
 * Classic token bucket: holds up to {@code capacity} tokens, refills at a
 * steady rate, each call consumes one. Allows short bursts up to the full
 * capacity while still capping the long-run average rate.
 */
final class TokenBucket {

	private final int capacity;
	private final double refillTokensPerNano;
	private double availableTokens;
	private long lastRefillNanos;

	TokenBucket(int capacity, int refillTokens, Duration refillPeriod, long nowNanos) {
		this.capacity = capacity;
		this.refillTokensPerNano = refillTokens / (double) refillPeriod.toNanos();
		this.availableTokens = capacity;
		this.lastRefillNanos = nowNanos;
	}

	synchronized boolean tryConsume(long nowNanos) {
		refill(nowNanos);
		if (availableTokens >= 1.0) {
			availableTokens -= 1.0;
			return true;
		}
		return false;
	}

	private void refill(long nowNanos) {
		long elapsedNanos = nowNanos - lastRefillNanos;
		if (elapsedNanos <= 0) {
			return;
		}
		availableTokens = Math.min(capacity, availableTokens + elapsedNanos * refillTokensPerNano);
		lastRefillNanos = nowNanos;
	}
}
