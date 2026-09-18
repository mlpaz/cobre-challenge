package com.cobre.notification.adapter.out.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.cobre.notification.adapter.out.idempotency.config.IdempotencyProperties;
import com.cobre.notification.domain.model.NotificationEvent;

class InMemoryIdempotencyStoreTest {

	private final NotificationEvent event = new NotificationEvent("EVT001", "credit_card_payment",
			"Payment received", Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001");

	@Test
	void isNotADuplicateBeforeItHasBeenMarkedAsProcessed() {
		InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(
				new IdempotencyProperties(Duration.ofMinutes(10)), Clock.systemUTC());

		assertThat(store.isDuplicate(event)).isFalse();
	}

	@Test
	void isADuplicateAfterBeingMarkedAsProcessed() {
		MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
		InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(
				new IdempotencyProperties(Duration.ofMinutes(10)), clock);

		store.markAsProcessed(event);

		assertThat(store.isDuplicate(event)).isTrue();
	}

	@Test
	void stopsBeingADuplicateOnceTheTtlExpires() {
		MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
		InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(
				new IdempotencyProperties(Duration.ofMinutes(10)), clock);

		store.markAsProcessed(event);
		clock.advance(Duration.ofMinutes(11));

		assertThat(store.isDuplicate(event)).isFalse();
	}

	@Test
	void doesNotCollideDifferentEventsOfTheSameTypeForTheSameClient() {
		InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(
				new IdempotencyProperties(Duration.ofMinutes(10)), Clock.systemUTC());
		NotificationEvent secondPayment = new NotificationEvent("EVT002", event.eventType(), "Another payment",
				event.deliveryDate(), event.clientId());

		store.markAsProcessed(event);

		assertThat(store.isDuplicate(secondPayment)).isFalse();
	}

	@Test
	void doesNotCollideTheSameEventIdAcrossDifferentClients() {
		InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(
				new IdempotencyProperties(Duration.ofMinutes(10)), Clock.systemUTC());
		NotificationEvent sameIdOtherClient = new NotificationEvent(event.eventId(), event.eventType(),
				event.content(), event.deliveryDate(), "CLIENT999");

		store.markAsProcessed(event);

		assertThat(store.isDuplicate(sameIdOtherClient)).isFalse();
	}

	private static final class MutableClock extends Clock {
		private Instant now;

		private MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
