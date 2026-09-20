package com.cobre.notification.domain.port.out;

/**
 * Per-webhook circuit breaker: each subscription (client_id + event_type)
 * tracks its own delivery success score, so a broken webhook only blocks
 * deliveries to that specific webhook — not to every other client's webhook.
 */
public interface WebhookCircuitBreakerPort {

	/**
	 * @return whether an automatic delivery attempt should proceed right now
	 *         for this webhook. Not consulted by the manual replay flow, which
	 *         always attempts delivery regardless of circuit state.
	 */
	boolean isCallPermitted(String clientId, String eventType);

	/**
	 * Records the outcome of a delivery attempt (automatic or replay) against
	 * this webhook, updating its score and circuit state.
	 */
	void recordResult(String clientId, String eventType, boolean success);
}
