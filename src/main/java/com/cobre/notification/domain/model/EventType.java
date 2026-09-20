package com.cobre.notification.domain.model;

/**
 * The fixed set of event types this service knows how to receive and
 * deliver — sourced from the case's sample data (notification_events.json).
 * A {@link NotificationEvent} whose {@code eventType} isn't one of these is
 * rejected at construction (see {@link com.cobre.notification.domain.exception.InvalidEventTypeException}),
 * regardless of whether it came in via HTTP or Kafka.
 */
public enum EventType {
	CREDIT_CARD_PAYMENT,
	DEBIT_CARD_WITHDRAWAL,
	CREDIT_TRANSFER,
	DEBIT_AUTOMATIC_PAYMENT,
	CREDIT_REFUND,
	DEBIT_TRANSFER,
	CREDIT_DEPOSIT,
	DEBIT_PURCHASE,
	CREDIT_CASHBACK,
	DEBIT_SUBSCRIPTION;

	/** @return whether {@code value} names one of these event types (case-insensitive). */
	public static boolean isValid(String value) {
		if (value == null) {
			return false;
		}
		for (EventType type : values()) {
			if (type.name().equalsIgnoreCase(value)) {
				return true;
			}
		}
		return false;
	}
}
