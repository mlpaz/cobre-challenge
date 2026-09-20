package com.cobre.notification.adapter.in.web.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * One token bucket per key (here, caller IP), in memory. Correct for a
 * single instance only — a multi-instance deployment needs a shared store
 * (e.g. Redis) so every instance enforces the same quota, and this map is
 * never evicted, so it grows with the number of distinct callers seen since
 * startup.
 */
class RateLimiter {

	private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
	private final int capacity;
	private final int refillTokens;
	private final Duration refillPeriod;
	private final LongSupplier nanoTimeSource;

	RateLimiter(int capacity, int refillTokens, Duration refillPeriod) {
		this(capacity, refillTokens, refillPeriod, System::nanoTime);
	}

	/** Visible for tests: allows controlling the passage of time. */
	RateLimiter(int capacity, int refillTokens, Duration refillPeriod, LongSupplier nanoTimeSource) {
		this.capacity = capacity;
		this.refillTokens = refillTokens;
		this.refillPeriod = refillPeriod;
		this.nanoTimeSource = nanoTimeSource;
	}

	boolean tryConsume(String key) {
		long now = nanoTimeSource.getAsLong();
		return buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillTokens, refillPeriod, now))
				.tryConsume(now);
	}
}
