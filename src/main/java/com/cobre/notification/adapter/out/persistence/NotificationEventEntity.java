package com.cobre.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.cobre.notification.domain.model.DeliveryStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only projection of a {@code notification_events} row. Writes go
 * through the native, atomic {@code INSERT ... ON CONFLICT} / {@code UPDATE}
 * queries in {@link NotificationEventJpaRepository} (see
 * {@link NotificationRecordJpaAdapter}), never through Hibernate's own
 * persistence lifecycle for this entity — that's what makes the claim in
 * {@code tryClaim}/{@code tryClaimForReplay} a single atomic statement
 * instead of a check-then-write race.
 */
@Entity
@Table(name = "notification_events")
public class NotificationEventEntity {

	@Id
	@Column(name = "notification_event_id", nullable = false, updatable = false)
	private UUID notificationEventId;

	@Column(name = "event_id", nullable = false, updatable = false)
	private String eventId;

	@Column(name = "client_id", nullable = false, updatable = false)
	private String clientId;

	@Column(name = "event_type", nullable = false)
	private String eventType;

	@Column(name = "content", nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "event_delivery_date", nullable = false)
	private Instant eventDeliveryDate;

	@Column(name = "delivery_status", nullable = false)
	@Enumerated(EnumType.STRING)
	private DeliveryStatus deliveryStatus;

	@Column(name = "webhook_response")
	private String webhookResponse;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	protected NotificationEventEntity() {
		// required by JPA
	}

	public UUID getNotificationEventId() {
		return notificationEventId;
	}

	public String getEventId() {
		return eventId;
	}

	public String getClientId() {
		return clientId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getContent() {
		return content;
	}

	public Instant getEventDeliveryDate() {
		return eventDeliveryDate;
	}

	public DeliveryStatus getDeliveryStatus() {
		return deliveryStatus;
	}

	public String getWebhookResponse() {
		return webhookResponse;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}
}
