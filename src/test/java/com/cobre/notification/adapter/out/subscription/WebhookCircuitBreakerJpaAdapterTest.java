package com.cobre.notification.adapter.out.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.cobre.notification.AbstractPostgresIntegrationTest;
import com.cobre.notification.adapter.out.subscription.config.WebhookCircuitBreakerProperties;
import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.model.WebhookCircuitState;
import com.cobre.notification.domain.port.out.MetricsPort;

/**
 * Full context against a real PostgreSQL instance (see
 * {@link AbstractPostgresIntegrationTest}), same approach as
 * SubscriptionJpaAdapterTest: Boot 4 dropped @DataJpaTest.
 */
@SpringBootTest
@Transactional
class WebhookCircuitBreakerJpaAdapterTest extends AbstractPostgresIntegrationTest {

	private static final String CLIENT_ID = "CLIENT001";
	private static final String EVENT_TYPE = "credit_card_payment";
	private static final String WEBHOOK_URL = "https://client.example.com/hooks/a";

	@Autowired
	private SubscriptionJpaAdapter subscriptionAdapter;

	@Autowired
	private SubscriptionJpaRepository repository;

	private final MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
	private final MetricsPort metricsPort = Mockito.mock(MetricsPort.class);

	// score-smoothing-factor=0.5: each new outcome carries as much weight as
	// the entire prior history, so scores are easy to hand-compute in assertions.
	private final WebhookCircuitBreakerProperties properties = new WebhookCircuitBreakerProperties(
			50, 3, Duration.ofSeconds(10), 2, 0.5);

	@Test
	void permitsCallsWhileClosed() {
		subscribe();

		assertThat(breaker().isCallPermitted(CLIENT_ID, EVENT_TYPE)).isTrue();
	}

	@Test
	void permitsCallsWhenThereIsNoSubscriptionRowToTrack() {
		assertThat(breaker().isCallPermitted("UNKNOWN_CLIENT", EVENT_TYPE)).isTrue();
	}

	@Test
	void updatesTheScoreAsAWeightedAverageOnEachRecordedResult() {
		subscribe();
		WebhookCircuitBreakerJpaAdapter breaker = breaker();

		breaker.recordResult(CLIENT_ID, EVENT_TYPE, false);
		assertThat(findEntity().getSuccessScore()).isEqualTo(50); // 100*0.5 + 0*0.5
		assertThat(findEntity().getTotalCalls()).isEqualTo(1);

		breaker.recordResult(CLIENT_ID, EVENT_TYPE, true);
		assertThat(findEntity().getSuccessScore()).isEqualTo(75); // 50*0.5 + 100*0.5
		assertThat(findEntity().getTotalCalls()).isEqualTo(2);
	}

	@Test
	void opensOnceEnoughFailuresDropTheScoreBelowTheThreshold() {
		subscribe();
		WebhookCircuitBreakerJpaAdapter breaker = breaker();

		breaker.recordResult(CLIENT_ID, EVENT_TYPE, false); // score 50, still CLOSED (only 1 call)
		breaker.recordResult(CLIENT_ID, EVENT_TYPE, false); // score 25, still CLOSED (only 2 calls)
		assertThat(findEntity().getCircuitState()).isEqualTo(WebhookCircuitState.CLOSED);

		breaker.recordResult(CLIENT_ID, EVENT_TYPE, false); // score 13, 3 calls >= minimum -> OPEN

		assertThat(findEntity().getCircuitState()).isEqualTo(WebhookCircuitState.OPEN);
		assertThat(findEntity().getSuccessScore()).isEqualTo(13);
		assertThat(breaker.isCallPermitted(CLIENT_ID, EVENT_TYPE)).isFalse();
		verify(metricsPort).increment("notification.webhook.circuit_opened", "event_type:" + EVENT_TYPE,
				"client_id:" + CLIENT_ID, "webhook:" + WEBHOOK_URL);
	}

	@Test
	void doesNotRepeatTheOpenedMetricWhileAlreadyOpen() {
		openTheCircuit();
		Mockito.clearInvocations(metricsPort);

		// Another failed replay while OPEN (score stays low, so it stays OPEN):
		breaker().recordResult(CLIENT_ID, EVENT_TYPE, false);

		assertThat(findEntity().getCircuitState()).isEqualTo(WebhookCircuitState.OPEN);
		Mockito.verifyNoInteractions(metricsPort);
	}

	@Test
	void blocksCallsWhileOpenAndTheWaitDurationHasNotElapsed() {
		openTheCircuit();
		clock.advance(Duration.ofSeconds(5));

		assertThat(breaker().isCallPermitted(CLIENT_ID, EVENT_TYPE)).isFalse();
	}

	@Test
	void movesToHalfOpenAndCapsTrialCallsOnceTheWaitElapses() {
		openTheCircuit();
		clock.advance(Duration.ofSeconds(10));
		WebhookCircuitBreakerJpaAdapter breaker = breaker();

		assertThat(breaker.isCallPermitted(CLIENT_ID, EVENT_TYPE)).isTrue();
		assertThat(findEntity().getCircuitState()).isEqualTo(WebhookCircuitState.HALF_OPEN);

		assertThat(breaker.isCallPermitted(CLIENT_ID, EVENT_TYPE)).isTrue(); // 2nd of 2 permitted trials
		assertThat(breaker.isCallPermitted(CLIENT_ID, EVENT_TYPE)).isFalse(); // trials exhausted, no result yet
	}

	@Test
	void closesAfterASuccessfulHalfOpenTrialRecoversTheScore() {
		openTheCircuit();
		clock.advance(Duration.ofSeconds(10));
		WebhookCircuitBreakerJpaAdapter breaker = breaker();
		breaker.isCallPermitted(CLIENT_ID, EVENT_TYPE); // moves to HALF_OPEN

		breaker.recordResult(CLIENT_ID, EVENT_TYPE, true); // score 13*0.5 + 100*0.5 = 57 (>= 50)

		SubscriptionEntity entity = findEntity();
		assertThat(entity.getCircuitState()).isEqualTo(WebhookCircuitState.CLOSED);
		assertThat(entity.getHalfOpenCalls()).isZero();
		assertThat(breaker.isCallPermitted(CLIENT_ID, EVENT_TYPE)).isTrue();
	}

	@Test
	void reopensImmediatelyWhenAHalfOpenTrialFails() {
		openTheCircuit();
		clock.advance(Duration.ofSeconds(10));
		WebhookCircuitBreakerJpaAdapter breaker = breaker();
		breaker.isCallPermitted(CLIENT_ID, EVENT_TYPE); // moves to HALF_OPEN
		Mockito.clearInvocations(metricsPort);

		breaker.recordResult(CLIENT_ID, EVENT_TYPE, false);

		SubscriptionEntity entity = findEntity();
		assertThat(entity.getCircuitState()).isEqualTo(WebhookCircuitState.OPEN);
		assertThat(entity.getCircuitOpenedAt()).isEqualTo(clock.instant());
		verify(metricsPort).increment("notification.webhook.circuit_opened", "event_type:" + EVENT_TYPE,
				"client_id:" + CLIENT_ID, "webhook:" + WEBHOOK_URL);
	}

	@Test
	void aRecoveredScoreClosesAnOpenCircuitEvenBeforeTheWaitElapses() {
		// Mirrors what a manual replay does: it bypasses isCallPermitted but
		// still calls recordResult, which can pull the circuit back to CLOSED
		// without waiting out the rest of the open window.
		openTheCircuit();
		WebhookCircuitBreakerJpaAdapter breaker = breaker();

		breaker.recordResult(CLIENT_ID, EVENT_TYPE, true); // score 13*0.5 + 100*0.5 = 57 (>= 50)

		assertThat(findEntity().getCircuitState()).isEqualTo(WebhookCircuitState.CLOSED);
	}

	private void openTheCircuit() {
		subscribe();
		WebhookCircuitBreakerJpaAdapter breaker = breaker();
		breaker.recordResult(CLIENT_ID, EVENT_TYPE, false);
		breaker.recordResult(CLIENT_ID, EVENT_TYPE, false);
		breaker.recordResult(CLIENT_ID, EVENT_TYPE, false);
	}

	private void subscribe() {
		subscriptionAdapter.save(new Subscription(CLIENT_ID, EVENT_TYPE, WEBHOOK_URL));
	}

	private SubscriptionEntity findEntity() {
		return repository.findByUserIdAndEventType(CLIENT_ID, EVENT_TYPE).orElseThrow();
	}

	private WebhookCircuitBreakerJpaAdapter breaker() {
		return new WebhookCircuitBreakerJpaAdapter(repository, properties, metricsPort, clock);
	}

	private static final class MutableClock extends Clock {
		private Instant now;

		private MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
