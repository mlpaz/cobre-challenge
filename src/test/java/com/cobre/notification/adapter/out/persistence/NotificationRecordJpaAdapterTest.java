package com.cobre.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.cobre.notification.AbstractPostgresIntegrationTest;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.model.RecoveredStuckEvent;

/**
 * Boot 4's spring-boot-test-autoconfigure dropped {@code @DataJpaTest}/
 * {@code @AutoConfigureTestDatabase}, so this runs the full context against
 * a real PostgreSQL instance (see {@link AbstractPostgresIntegrationTest})
 * instead of a JPA test slice, with Flyway running the real migration to
 * build the schema. Real Postgres matters even more here than usual: the
 * whole point of {@code tryClaim} is a single atomic statement, and only a
 * real database can prove {@link #onlyOneOfManyConcurrentClaimsForTheSameEventWins()}.
 */
@SpringBootTest
@Transactional
class NotificationRecordJpaAdapterTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private NotificationRecordJpaAdapter adapter;

	@Autowired
	private NotificationEventJpaRepository repository;

	private final NotificationEvent event = new NotificationEvent("EVT001", "credit_card_payment",
			"Payment received", Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001");

	@Test
	void claimsAFreshEventAndInsertsItAsProcessing() {
		boolean claimed = adapter.tryClaim(event);

		assertThat(claimed).isTrue();
		NotificationEventEntity saved = repository.findByClientIdAndEventId("CLIENT001", "EVT001").orElseThrow();
		assertThat(saved.getNotificationEventId()).isNotNull();
		assertThat(saved.getEventType()).isEqualTo("credit_card_payment");
		assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.PROCESSING);
	}

	@Test
	void completeWritesTheFinalOutcomeOfAClaimedEvent() {
		adapter.tryClaim(event);

		adapter.complete(event, new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, "ref-123"));

		NotificationEventEntity saved = repository.findByClientIdAndEventId("CLIENT001", "EVT001").orElseThrow();
		assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
		assertThat(saved.getWebhookResponse()).isEqualTo("ref-123");
	}

	@Test
	void claimingAnAlreadyDeliveredEventFails() {
		adapter.tryClaim(event);
		adapter.complete(event, new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, "ref-123"));

		boolean claimedAgain = adapter.tryClaim(event);

		assertThat(claimedAgain).isFalse();
		// The already-DELIVERED row is untouched -- a lost claim never overwrites it.
		NotificationEventEntity saved = repository.findByClientIdAndEventId("CLIENT001", "EVT001").orElseThrow();
		assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
	}

	@Test
	void reclaimingAFailedEventSucceedsAndKeepsTheSameId() {
		adapter.tryClaim(event);
		adapter.complete(event, new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null));
		NotificationEventEntity firstAttempt = repository.findByClientIdAndEventId("CLIENT001", "EVT001").orElseThrow();

		boolean reclaimed = adapter.tryClaim(event);

		assertThat(reclaimed).isTrue();
		NotificationEventEntity saved = repository.findByClientIdAndEventId("CLIENT001", "EVT001").orElseThrow();
		assertThat(saved.getNotificationEventId()).isEqualTo(firstAttempt.getNotificationEventId());
		assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.PROCESSING);
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void reclaimingACircuitOpenEventSucceeds() {
		adapter.tryClaim(event);
		adapter.complete(event, new DeliveryResult(event.eventId(), DeliveryStatus.CIRCUIT_OPEN, null));

		assertThat(adapter.tryClaim(event)).isTrue();
	}

	@Test
	void onlyOneOfManyConcurrentClaimsForTheSameEventWins() throws Exception {
		// This is the actual regression test for the P1 finding: isDuplicate()
		// and markAsProcessed() used to be two separate steps, so N concurrent
		// callers could all pass the check and all call the webhook. tryClaim
		// is a single INSERT ... ON CONFLICT statement instead, so only one of
		// N concurrent callers may ever win, no matter how they interleave.
		//
		// A dedicated event/client id, not the shared `event` field: the claims
		// below run on separate threads with their own connections and commit
		// for real (outside this test method's @Transactional rollback, which
		// only binds the main test thread) — reusing "EVT001"/"CLIENT001" would
		// leak a committed row into every other test in this class.
		NotificationEvent concurrentEvent = new NotificationEvent("EVT-CONCURRENCY", "credit_card_payment",
				"Payment received", Instant.parse("2024-03-15T09:30:22Z"), "CLIENT-CONCURRENCY");
		int attempts = 10;
		ExecutorService pool = Executors.newFixedThreadPool(attempts);
		try {
			Callable<Boolean> claimAttempt = () -> adapter.tryClaim(concurrentEvent);
			List<Callable<Boolean>> tasks = IntStream.range(0, attempts).<Callable<Boolean>>mapToObj(i -> claimAttempt)
					.toList();
			List<Future<Boolean>> results = pool.invokeAll(tasks);

			long winners = 0;
			for (Future<Boolean> result : results) {
				if (result.get()) {
					winners++;
				}
			}
			assertThat(winners).isEqualTo(1);
		} finally {
			// Cleanup must also run on a pool thread (its own connection/
			// transaction, actually committed) — issued from the main test
			// thread it would join, then be undone by, the ambient rollback.
			pool.submit(() -> repository
					.findByClientIdAndEventId(concurrentEvent.clientId(), concurrentEvent.eventId())
					.ifPresent(repository::delete)).get(5, TimeUnit.SECONDS);
			pool.shutdown();
			pool.awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	@Test
	void recoverStuckProcessingFlipsOldClaimedRowsToFailed() {
		adapter.tryClaim(event);

		List<RecoveredStuckEvent> recovered = adapter.recoverStuckProcessing(Instant.now().plusSeconds(60));

		assertThat(recovered).containsExactly(new RecoveredStuckEvent("CLIENT001", "credit_card_payment"));
		NotificationEventEntity saved = repository.findByClientIdAndEventId("CLIENT001", "EVT001").orElseThrow();
		assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
	}

	@Test
	void recoverStuckProcessingLeavesRecentClaimsAlone() {
		adapter.tryClaim(event);

		List<RecoveredStuckEvent> recovered = adapter.recoverStuckProcessing(Instant.now().minusSeconds(60));

		assertThat(recovered).isEmpty();
		NotificationEventEntity saved = repository.findByClientIdAndEventId("CLIENT001", "EVT001").orElseThrow();
		assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.PROCESSING);
	}
}
