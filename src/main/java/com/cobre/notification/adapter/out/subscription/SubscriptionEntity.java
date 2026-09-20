package com.cobre.notification.adapter.out.subscription;

import java.time.Instant;
import java.util.UUID;

import com.cobre.notification.domain.model.Subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * What a client subscribed to — the webhook's own runtime health (score,
 * circuit breaker state) lives in a separate table/entity, see
 * {@link com.cobre.notification.adapter.out.circuitbreaker.WebhookCircuitBreakerEntity}.
 */
@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {

	@Id
	@Column(name = "subscription_id", nullable = false, updatable = false)
	private UUID subscriptionId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private String userId;

	@Column(name = "event_type", nullable = false, updatable = false)
	private String eventType;

	@Column(name = "web_hook_url", nullable = false)
	private String webHookUrl;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SubscriptionEntity() {
		// required by JPA
	}

	SubscriptionEntity(Subscription subscription, Instant createdAt) {
		this.subscriptionId = UUID.randomUUID();
		this.userId = subscription.userId();
		this.eventType = subscription.eventType();
		this.webHookUrl = subscription.webHookUrl();
		this.createdAt = createdAt;
	}

	void updateWebHookUrl(String webHookUrl) {
		this.webHookUrl = webHookUrl;
	}

	public UUID getSubscriptionId() {
		return subscriptionId;
	}

	public String getUserId() {
		return userId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getWebHookUrl() {
		return webHookUrl;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
