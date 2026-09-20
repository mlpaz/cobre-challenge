package com.cobre.notification.adapter.out.circuitbreaker;

import java.time.Instant;
import java.util.UUID;

import com.cobre.notification.domain.model.WebhookCircuitState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Runtime health of a single physical webhook — one row per
 * {@code (client_id, web_hook_url)}, not per subscription: a client can
 * point several event types at the exact same URL, and their delivery
 * outcomes should count against the same breaker instead of each event type
 * opening (or staying closed) independently. Deliberately not part of
 * {@code SubscriptionEntity}: what a client subscribed to and how a webhook
 * has been behaving are different concerns, on different lifecycles.
 */
@Entity
@Table(name = "webhook_circuit_breakers")
public class WebhookCircuitBreakerEntity {

	@Id
	@Column(name = "webhook_circuit_breaker_id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "client_id", nullable = false, updatable = false)
	private String clientId;

	@Column(name = "web_hook_url", nullable = false, updatable = false)
	private String webHookUrl;

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

	protected WebhookCircuitBreakerEntity() {
		// required by JPA
	}

	/**
	 * Starts a fresh (healthy) breaker for a (client, webhook URL) pair that
	 * has never been seen before. Public: created from
	 * {@code SubscriptionJpaAdapter} (a different package), which owns the
	 * "find or create the breaker for this client's webhook" logic.
	 */
	public WebhookCircuitBreakerEntity(String clientId, String webHookUrl) {
		this.id = UUID.randomUUID();
		this.clientId = clientId;
		this.webHookUrl = webHookUrl;
		this.successScore = 100;
		this.totalCalls = 0;
		this.circuitState = WebhookCircuitState.CLOSED;
		this.circuitOpenedAt = null;
		this.halfOpenCalls = 0;
	}

	/** Applied by the webhook circuit breaker adapter after computing the next state. */
	void recordState(int successScore, long totalCalls, WebhookCircuitState circuitState, Instant circuitOpenedAt,
			int halfOpenCalls) {
		this.successScore = successScore;
		this.totalCalls = totalCalls;
		this.circuitState = circuitState;
		this.circuitOpenedAt = circuitOpenedAt;
		this.halfOpenCalls = halfOpenCalls;
	}

	public UUID getId() {
		return id;
	}

	public String getClientId() {
		return clientId;
	}

	public String getWebHookUrl() {
		return webHookUrl;
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
