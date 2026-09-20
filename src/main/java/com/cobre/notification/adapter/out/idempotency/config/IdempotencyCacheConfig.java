package com.cobre.notification.adapter.out.idempotency.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.cobre.notification.adapter.out.idempotency.InMemoryIdempotencyCache;
import com.cobre.notification.adapter.out.idempotency.RedisIdempotencyCache;
import com.cobre.notification.domain.port.out.IdempotencyPort;

/**
 * Picks the {@link IdempotencyPort} implementation by active profile: the
 * {@code local} profile gets an in-memory stand-in (no Redis instance
 * required to run the app locally), everything else gets the real Redis
 * cache — see the README's "Concurrencia y race conditions" section for why
 * this split is safe (this port is a fast-path hint, never the source of
 * truth).
 */
@Configuration
public class IdempotencyCacheConfig {

	@Bean
	@Profile("local")
	public IdempotencyPort inMemoryIdempotencyCache(IdempotencyProperties properties) {
		return new InMemoryIdempotencyCache(properties);
	}

	@Bean
	@Profile("!local")
	public IdempotencyPort redisIdempotencyCache(StringRedisTemplate redisTemplate, IdempotencyProperties properties) {
		return new RedisIdempotencyCache(redisTemplate, properties);
	}
}
