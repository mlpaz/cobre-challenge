package com.cobre.notification.adapter.out.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.cobre.notification.adapter.out.idempotency.config.IdempotencyProperties;
import com.cobre.notification.domain.model.NotificationEvent;

/**
 * Runs against a real Redis instance (Testcontainers), not a mock — same
 * reasoning as every other adapter test in this project (see the README's
 * "Tests" section): a fake would only prove the code compiles against the
 * client API, not that {@code SET ... EX} and {@code GET} actually behave
 * the way this class assumes.
 *
 * <p>Unlike the Postgres-backed adapter tests, this class isn't wrapped in a
 * rolled-back transaction (Redis has no such concept) — every test method
 * uses its own {@code event_id} so they can never see each other's keys,
 * regardless of execution order.
 */
@Testcontainers
class RedisIdempotencyCacheTest {

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redisTemplate;

	@BeforeAll
	static void setUpRedisTemplate() {
		RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(REDIS.getHost(),
				REDIS.getMappedPort(6379));
		connectionFactory = new LettuceConnectionFactory(config);
		connectionFactory.afterPropertiesSet();
		redisTemplate = new StringRedisTemplate(connectionFactory);
		redisTemplate.afterPropertiesSet();
	}

	@AfterAll
	static void tearDownRedisTemplate() {
		connectionFactory.destroy();
	}

	private static NotificationEvent event(String eventId) {
		return new NotificationEvent(eventId, "credit_card_payment", "Payment received",
				Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001");
	}

	@Test
	void isNotADuplicateBeforeItHasBeenMarkedAsProcessed() {
		RedisIdempotencyCache cache = new RedisIdempotencyCache(redisTemplate,
				new IdempotencyProperties(Duration.ofMinutes(10)));

		assertThat(cache.isDuplicate(event("EVT-fresh"))).isFalse();
	}

	@Test
	void isADuplicateAfterBeingMarkedAsProcessed() {
		RedisIdempotencyCache cache = new RedisIdempotencyCache(redisTemplate,
				new IdempotencyProperties(Duration.ofMinutes(10)));
		NotificationEvent event = event("EVT-marked");

		cache.markAsProcessed(event);

		assertThat(cache.isDuplicate(event)).isTrue();
	}

	@Test
	void doesNotCollideDifferentEventsOfTheSameTypeForTheSameClient() {
		RedisIdempotencyCache cache = new RedisIdempotencyCache(redisTemplate,
				new IdempotencyProperties(Duration.ofMinutes(10)));

		cache.markAsProcessed(event("EVT-pair-1"));

		assertThat(cache.isDuplicate(event("EVT-pair-2"))).isFalse();
	}

	@Test
	void treatsTheSameEventIdAsADuplicateRegardlessOfOtherFields() {
		RedisIdempotencyCache cache = new RedisIdempotencyCache(redisTemplate,
				new IdempotencyProperties(Duration.ofMinutes(10)));
		NotificationEvent original = event("EVT-same-id");
		NotificationEvent sameIdOtherClient = new NotificationEvent("EVT-same-id", original.eventType(),
				original.content(), original.deliveryDate(), "CLIENT999");

		cache.markAsProcessed(original);

		assertThat(cache.isDuplicate(sameIdOtherClient)).isTrue();
	}

	@Test
	void stopsBeingADuplicateOnceTheTtlExpires() throws InterruptedException {
		RedisIdempotencyCache cache = new RedisIdempotencyCache(redisTemplate,
				new IdempotencyProperties(Duration.ofMillis(300)));
		NotificationEvent shortTtlEvent = event("EVT-ttl");

		cache.markAsProcessed(shortTtlEvent);
		assertThat(cache.isDuplicate(shortTtlEvent)).isTrue();

		Thread.sleep(500);

		assertThat(cache.isDuplicate(shortTtlEvent)).isFalse();
	}
}
