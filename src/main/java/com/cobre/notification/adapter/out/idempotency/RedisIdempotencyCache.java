package com.cobre.notification.adapter.out.idempotency;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.cobre.notification.adapter.out.idempotency.config.IdempotencyProperties;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.out.IdempotencyPort;

/**
 * Production implementation of the fast-path duplicate cache: a Redis
 * {@code SET key EX ttl} per processed event, checked with a plain
 * {@code GET}. Unlike {@link InMemoryIdempotencyCache}, this one is shared
 * across every instance of the service, so it also catches the common case
 * of a duplicate landing on a <em>different</em> node than the one that
 * first delivered it, without ever reaching Postgres for it — see
 * "Concurrencia y race conditions" in the README. It's still just a hint,
 * though: {@link com.cobre.notification.domain.port.out.NotificationRecordPort#tryClaim}
 * is what actually guarantees no double delivery, with or without this
 * cache being available.
 */
public class RedisIdempotencyCache implements IdempotencyPort {

	private final StringRedisTemplate redisTemplate;
	private final Duration ttl;

	public RedisIdempotencyCache(StringRedisTemplate redisTemplate, IdempotencyProperties properties) {
		this.redisTemplate = redisTemplate;
		this.ttl = properties.ttl();
	}

	@Override
	public boolean isDuplicate(NotificationEvent event) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key(event)));
	}

	@Override
	public void markAsProcessed(NotificationEvent event) {
		redisTemplate.opsForValue().set(key(event), "1", ttl);
	}

	private static String key(NotificationEvent event) {
		return "idempotency:" + event.eventId();
	}
}
