package com.cobre.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.cobre.notification.domain.exception.InvalidEventTypeException;

class NotificationEventTest {

	@Test
	void acceptsAnyKnownEventType() {
		assertThatCode(() -> new NotificationEvent("EVT001", "credit_card_payment", "Payment received",
				Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001")).doesNotThrowAnyException();
	}

	@Test
	void acceptsAKnownEventTypeRegardlessOfCase() {
		assertThatCode(() -> new NotificationEvent("EVT001", "CREDIT_CARD_PAYMENT", "Payment received",
				Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001")).doesNotThrowAnyException();
	}

	@Test
	void rejectsAnEventTypeThatIsNotInTheKnownList() {
		assertThatThrownBy(() -> new NotificationEvent("EVT001", "banana_payment", "Payment received",
				Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001"))
				.isInstanceOf(InvalidEventTypeException.class)
				.hasMessageContaining("banana_payment");
	}
}
