package com.cobre.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.cobre.notification.AbstractPostgresIntegrationTest;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;

/**
 * Boot 4's spring-boot-test-autoconfigure dropped {@code @DataJpaTest}/
 * {@code @AutoConfigureTestDatabase}, so this runs the full context against
 * a real PostgreSQL instance (see {@link AbstractPostgresIntegrationTest})
 * instead of a JPA test slice, with Flyway running the real migration to
 * build the schema.
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
	void insertsANewRowTheFirstTimeAnEventIsSaved() {
		adapter.save(event, new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, "ref-123"));

		NotificationEventEntity saved = repository.findByClientIdAndEventId("CLIENT001", "EVT001").orElseThrow();
		assertThat(saved.getNotificationEventId()).isNotNull();
		assertThat(saved.getEventType()).isEqualTo("credit_card_payment");
		assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
		assertThat(saved.getWebhookResponse()).isEqualTo("ref-123");
	}

	@Test
	void updatesTheExistingRowInsteadOfInsertingADuplicateOnASecondSave() {
		adapter.save(event, new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null));
		NotificationEventEntity firstAttempt = repository.findByClientIdAndEventId("CLIENT001", "EVT001").orElseThrow();

		adapter.save(event, new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, "ref-456"));

		assertThat(repository.count()).isEqualTo(1);
		NotificationEventEntity afterReplay = repository.findByClientIdAndEventId("CLIENT001", "EVT001").orElseThrow();
		assertThat(afterReplay.getNotificationEventId()).isEqualTo(firstAttempt.getNotificationEventId());
		assertThat(afterReplay.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
		assertThat(afterReplay.getWebhookResponse()).isEqualTo("ref-456");
	}
}
