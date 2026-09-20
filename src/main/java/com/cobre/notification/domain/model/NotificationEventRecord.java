package com.cobre.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model for a persisted notification event row, as exposed by the
 * notification_events query/replay API. Distinct from {@link NotificationEvent},
 * which is the inbound event to be delivered.
 */
public record NotificationEventRecord(
		UUID notificationEventId,
		String eventId,
		String eventType,
		String content,
		String clientId,
		Instant eventDeliveryDate,
		DeliveryStatus deliveryStatus,
		String webhookResponse,
		Instant processedAt) {
}
