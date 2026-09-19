package com.cobre.notification.adapter.out.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import com.cobre.notification.adapter.out.idempotency.config.IdempotencyProperties;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.out.IdempotencyPort;

/**
 * Stand-in for a shared TTL-based key-value store (e.g. Redis {@code SET key
 * value EX ttl}). Only correct for a single instance: a multi-instance
 * deployment needs a real shared KVS so every instance sees the same dedup
 * state, otherwise two instances could each deliver the same event once.
 *
 * <p>Keyed by {@code event_id} alone: event ids are unique platform-wide, so
 * no other field is needed to identify one specific event occurrence.
 */
public class InMemoryIdempotencyStore implements IdempotencyPort {

	private final ConcurrentHashMap<String, Instant> processedUntil = new ConcurrentHashMap<>();
	private final Duration ttl;
	private final Clock clock;

	public InMemoryIdempotencyStore(IdempotencyProperties properties) {
		this(properties, Clock.systemUTC());
	}

	/** Visible for tests: allows controlling time to exercise TTL expiry. */
	InMemoryIdempotencyStore(IdempotencyProperties properties, Clock clock) {
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
