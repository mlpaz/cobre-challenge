package com.cobre.notification.adapter.out.subscription;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.cobre.notification.adapter.out.subscription.config.WebhookCircuitBreakerProperties;
import com.cobre.notification.domain.model.WebhookCircuitState;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.MetricsTags;
import com.cobre.notification.domain.port.out.WebhookCircuitBreakerPort;

/**
 * Circuit breaker scoped to a single webhook (one subscription row), so a
 * broken webhook only blocks deliveries to itself — never to another
 * client's webhook, the way a single breaker shared across every webhook
 * would.
 *
 * <p>The score is an exponential moving average of recent outcomes (100 =
 * success, 0 = failure), so recent behavior matters more than old history
 * without needing to persist a literal call-by-call window. State
 * transitions otherwise follow a standard circuit breaker: CLOSED while
 * healthy; OPEN (fail fast, no HTTP call to the webhook) once the score
 * drops below the configured threshold after enough calls; HALF_OPEN, after
 * the open wait elapses, lets a limited number of trial calls through to
 * decide whether to close again or reopen.
 *
 * <p>Not component-scanned: wired as a bean by
 * {@link com.cobre.notification.adapter.out.subscription.config.WebhookCircuitBreakerConfig}
 * instead, since a second (test-only) constructor overload would otherwise
 * make Spring's constructor-autowiring resolution ambiguous.
 */
public class WebhookCircuitBreakerJpaAdapter implements WebhookCircuitBreakerPort {

	private static final Logger log = LoggerFactory.getLogger(WebhookCircuitBreakerJpaAdapter.class);

	private final SubscriptionJpaRepository repository;
	private final WebhookCircuitBreakerProperties properties;
	private final MetricsPort metricsPort;
	private final Clock clock;

	public WebhookCircuitBreakerJpaAdapter(SubscriptionJpaRepository repository,
			WebhookCircuitBreakerProperties properties, MetricsPort metricsPort) {
		this(repository, properties, metricsPort, Clock.systemUTC());
	}

	/** Visible for tests: allows controlling time to exercise the open-state wait. */
	WebhookCircuitBreakerJpaAdapter(SubscriptionJpaRepository repository, WebhookCircuitBreakerProperties properties,
			MetricsPort metricsPort, Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.metricsPort = metricsPort;
		this.clock = clock;
	}

	@Override
	@Transactional
	public boolean isCallPermitted(String clientId, String eventType) {
		SubscriptionEntity entity = repository.findByUserIdAndEventType(clientId, eventType).orElse(null);
		if (entity == null) {
			// No subscription row to track — nothing for this port to gate on.
			return true;
		}

		WebhookCircuitState state = entity.getCircuitState();
		if (state == WebhookCircuitState.CLOSED) {
			return true;
		}

		if (state == WebhookCircuitState.OPEN) {
			Instant openedAt = entity.getCircuitOpenedAt();
			boolean waitElapsed = openedAt != null
					&& !clock.instant().isBefore(openedAt.plus(properties.waitDurationInOpenState()));
			if (!waitElapsed) {
				return false;
			}
			// Wait elapsed: move to HALF_OPEN and let this call through as the first trial.
			entity.recordCircuitState(entity.getSuccessScore(), entity.getTotalCalls(), WebhookCircuitState.HALF_OPEN,
					null, 1);
			return true;
		}

		// HALF_OPEN: allow up to the configured number of trial calls through.
		if (entity.getHalfOpenCalls() < properties.permittedNumberOfCallsInHalfOpenState()) {
			entity.recordCircuitState(entity.getSuccessScore(), entity.getTotalCalls(), WebhookCircuitState.HALF_OPEN,
					null, entity.getHalfOpenCalls() + 1);
			return true;
		}
		return false;
	}

	@Override
	@Transactional
	public void recordResult(String clientId, String eventType, boolean success) {
		SubscriptionEntity entity = repository.findByUserIdAndEventType(clientId, eventType).orElse(null);
		if (entity == null) {
			return;
		}

		WebhookCircuitState previousState = entity.getCircuitState();
		int newScore = updatedScore(entity.getSuccessScore(), success);
		long newTotalCalls = entity.getTotalCalls() + 1;
		Instant now = clock.instant();

		WebhookCircuitState newState;
		Instant newOpenedAt;
		int newHalfOpenCalls;

		switch (previousState) {
			case CLOSED -> {
				boolean shouldOpen = newTotalCalls >= properties.minimumNumberOfCalls()
						&& newScore < properties.minSuccessScore();
				newState = shouldOpen ? WebhookCircuitState.OPEN : WebhookCircuitState.CLOSED;
				newOpenedAt = shouldOpen ? now : null;
				newHalfOpenCalls = 0;
			}
			case OPEN -> {
				// Only reachable via a manual replay — isCallPermitted blocks the
				// automatic flow while OPEN. A recovered score closes the circuit
				// early instead of waiting out the rest of the open window.
				boolean recovered = newScore >= properties.minSuccessScore();
				newState = recovered ? WebhookCircuitState.CLOSED : WebhookCircuitState.OPEN;
				newOpenedAt = recovered ? null : entity.getCircuitOpenedAt();
				newHalfOpenCalls = 0;
			}
			default -> { // HALF_OPEN
				if (!success) {
					newState = WebhookCircuitState.OPEN;
					newOpenedAt = now;
					newHalfOpenCalls = 0;
				} else if (newScore >= properties.minSuccessScore()) {
					newState = WebhookCircuitState.CLOSED;
					newOpenedAt = null;
					newHalfOpenCalls = 0;
				} else if (entity.getHalfOpenCalls() >= properties.permittedNumberOfCallsInHalfOpenState()) {
					newState = WebhookCircuitState.OPEN;
					newOpenedAt = now;
					newHalfOpenCalls = 0;
				} else {
					newState = WebhookCircuitState.HALF_OPEN;
					newOpenedAt = null;
					newHalfOpenCalls = entity.getHalfOpenCalls();
				}
			}
		}

		entity.recordCircuitState(newScore, newTotalCalls, newState, newOpenedAt, newHalfOpenCalls);

		if (newState == WebhookCircuitState.OPEN && previousState != WebhookCircuitState.OPEN) {
			log.warn("Circuit breaker opened for webhook client={} eventType={} score={}", clientId, eventType,
					newScore);
			metricsPort.increment("notification.webhook.circuit_opened", MetricsTags.EVENT_TYPE.of(eventType),
					MetricsTags.CLIENT_ID.of(clientId), MetricsTags.WEBHOOK.of(entity.getWebHookUrl()));
		}
	}

	private int updatedScore(int currentScore, boolean success) {
		double alpha = properties.scoreSmoothingFactor();
		double outcome = success ? 100.0 : 0.0;
		return (int) Math.round(currentScore * (1 - alpha) + outcome * alpha);
	}
}
