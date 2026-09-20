package com.cobre.notification.adapter.out.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import com.cobre.notification.adapter.out.idempotency.config.IdempotencyProperties;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.out.IdempotencyPort;

/**
 * Local stand-in for {@link RedisIdempotencyCache}, used when running with
 * the {@code local} profile so a developer doesn't need a Redis instance
 * just to run the app — see {@link com.cobre.notification.adapter.out.idempotency.config.IdempotencyCacheConfig}.
 *
 * <p>Only correct as the fast-path hint it's meant to be within a single
 * instance: a multi-instance deployment doesn't use this one (see the
 * config), precisely because two instances wouldn't see each other's
 * entries. That's fine for local dev (one instance) and never a correctness
 * issue anywhere else, since {@link com.cobre.notification.domain.port.out.NotificationRecordPort#tryClaim}
 * is what actually guarantees no double delivery regardless of which cache
 * (or none) answered first.
 *
 * <p>Keyed by {@code event_id} alone: event ids are unique platform-wide, so
 * no other field is needed to identify one specific event occurrence.
 */
public class InMemoryIdempotencyCache implements IdempotencyPort {

	private final ConcurrentHashMap<String, Instant> processedUntil = new ConcurrentHashMap<>();
	private final Duration ttl;
	private final Clock clock;

	public InMemoryIdempotencyCache(IdempotencyProperties properties) {
		this(properties, Clock.systemUTC());
	}

	/** Visible for tests: allows controlling time to exercise TTL expiry. */
	InMemoryIdempotencyCache(IdempotencyProperties properties, Clock clock) {
		this.ttl = properties.ttl();
		this.clock = clock;
	}

	@Override
	public boolean isDuplicate(NotificationEvent event) {
		Instant expiresAt = processedUntil.get(key(event));
		return expiresAt != null && expiresAt.isAfter(clock.instant());
	}

	@Override
	public void markAsProcessed(NotificationEvent event) {
		processedUntil.put(key(event), clock.instant().plus(ttl));
	}

	private static String key(NotificationEvent event) {
		return event.eventId();
	}
}
