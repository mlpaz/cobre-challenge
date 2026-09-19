package com.cobre.notification.adapter.in.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

	@Test
	void permitsCallsUpToTheConfiguredCapacity() {
		AtomicLong nanoTime = new AtomicLong(0);
		RateLimiter limiter = new RateLimiter(3, 3, Duration.ofSeconds(1), nanoTime::get);

		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isFalse();
	}

	@Test
	void tracksEachKeyIndependently() {
		AtomicLong nanoTime = new AtomicLong(0);
		RateLimiter limiter = new RateLimiter(1, 1, Duration.ofSeconds(1), nanoTime::get);

		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isFalse();
		assertThat(limiter.tryConsume("5.6.7.8")).isTrue();
	}

	@Test
	void refillsTokensOverTimeUpToCapacity() {
		AtomicLong nanoTime = new AtomicLong(0);
		RateLimiter limiter = new RateLimiter(2, 2, Duration.ofSeconds(1), nanoTime::get);

		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isFalse();

		nanoTime.set(Duration.ofMillis(500).toNanos()); // half the refill period -> +1 token

		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isFalse();

		nanoTime.set(Duration.ofSeconds(10).toNanos()); // well past a full refill -> capped at capacity

		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
		assertThat(limiter.tryConsume("1.2.3.4")).isFalse();
	}
}
