package com.cobre.notification.adapter.out.subscription;

import java.time.Instant;
import java.util.UUID;

import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.model.WebhookCircuitState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

	@Column(name = "success_score", nullable = false)
	private int successScore;

	@Column(name = "total_calls", nullable = false)
	private long totalCalls;

	@Column(name = "circuit_state", nullable = false)
	@Enumerated(EnumType.STRING)
	private WebhookCircuitState circuitState;

	@Column(name = "circuit_opened_at")
	private Instant circuitOpenedAt;

	@Column(name = "half_open_calls", nullable = false)
	private int halfOpenCalls;

	protected SubscriptionEntity() {
		// required by JPA
	}

	SubscriptionEntity(Subscription subscription, Instant createdAt) {
		this.subscriptionId = UUID.randomUUID();
		this.userId = subscription.userId();
		this.eventType = subscription.eventType();
		this.webHookUrl = subscription.webHookUrl();
		this.createdAt = createdAt;
		resetCircuitBreaker();
	}

	/**
	 * A changed webhook URL is potentially a different endpoint, so its
	 * reliability history is reset instead of carried over. Re-submitting the
	 * same URL is a no-op for the circuit breaker state.
	 */
	void updateWebHookUrl(String webHookUrl) {
		if (!this.webHookUrl.equals(webHookUrl)) {
			resetCircuitBreaker();
		}
		this.webHookUrl = webHookUrl;
	}

	private void resetCircuitBreaker() {
		this.successScore = 100;
		this.totalCalls = 0;
		this.circuitState = WebhookCircuitState.CLOSED;
		this.circuitOpenedAt = null;
		this.halfOpenCalls = 0;
	}

	/** Applied by the webhook circuit breaker adapter after computing the next state. */
	void recordCircuitState(int successScore, long totalCalls, WebhookCircuitState circuitState,
			Instant circuitOpenedAt, int halfOpenCalls) {
		this.successScore = successScore;
		this.totalCalls = totalCalls;
		this.circuitState = circuitState;
		this.circuitOpenedAt = circuitOpenedAt;
		this.halfOpenCalls = halfOpenCalls;
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

	public int getSuccessScore() {
		return successScore;
	}

	public long getTotalCalls() {
		return totalCalls;
	}

	public WebhookCircuitState getCircuitState() {
		return circuitState;
	}

	public Instant getCircuitOpenedAt() {
		return circuitOpenedAt;
	}

	public int getHalfOpenCalls() {
		return halfOpenCalls;
	}
}
