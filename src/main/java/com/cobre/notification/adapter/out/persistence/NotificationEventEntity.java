package com.cobre.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

	@Column(name = "provider_reference")
	private String providerReference;

	@Column(name = "processed_at", nullable = false)
	private Instant processedAt;

	protected NotificationEventEntity() {
		// required by JPA
	}

	NotificationEventEntity(NotificationEvent event, DeliveryResult result, Instant processedAt) {
		this.notificationEventId = UUID.randomUUID();
		this.eventId = event.eventId();
		this.clientId = event.clientId();
		applyResult(event, result, processedAt);
	}

	void applyResult(NotificationEvent event, DeliveryResult result, Instant processedAt) {
		this.eventType = event.eventType();
		this.content = event.content();
		this.eventDeliveryDate = event.deliveryDate();
		this.deliveryStatus = result.status();
		this.providerReference = result.providerReference();
		this.processedAt = processedAt;
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

	public String getProviderReference() {
		return providerReference;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}
}
