package com.cobre.notification.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.model.NotificationEventQuery;
import com.cobre.notification.domain.model.NotificationEventRecord;
import com.cobre.notification.domain.model.PagedResult;

/**
 * Full context against an H2 database (PostgreSQL compatibility mode), same
 * approach as {@link NotificationRecordJpaAdapterTest}: Boot 4 dropped the
 * {@code @DataJpaTest} slice, and the native LIMIT/OFFSET query needs a real
 * SQL dialect to exercise.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:notification-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password="
})
@Transactional
class NotificationEventQueryJpaAdapterTest {

	@Autowired
	private NotificationEventQueryJpaAdapter adapter;

	@Autowired
	private NotificationRecordJpaAdapter recordAdapter;

	@Test
	void findsAPersistedEventById() {
		save("EVT001", "CLIENT001", Instant.parse("2024-03-15T09:30:22Z"), DeliveryStatus.DELIVERED, "ref-1");
		UUID id = idOf("CLIENT001", "EVT001");

		Optional<NotificationEventRecord> found = adapter.findById(id);

		assertThat(found).isPresent();
		assertThat(found.get().eventId()).isEqualTo("EVT001");
		assertThat(found.get().deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
	}

	@Test
	void returnsEmptyWhenNoEventExistsWithThatId() {
		assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
	}

	@Test
	void filtersByClientDeliveryStatusAndDateRangeOrderedByDeliveryDateDescending() {
		save("EVT001", "CLIENT001", Instant.parse("2024-03-10T00:00:00Z"), DeliveryStatus.FAILED, null);
		save("EVT002", "CLIENT001", Instant.parse("2024-03-15T00:00:00Z"), DeliveryStatus.DELIVERED, "ref-2");
		save("EVT003", "CLIENT001", Instant.parse("2024-03-20T00:00:00Z"), DeliveryStatus.DELIVERED, "ref-3");
		save("EVT004", "CLIENT002", Instant.parse("2024-03-18T00:00:00Z"), DeliveryStatus.DELIVERED, "ref-4");

		NotificationEventQuery query = new NotificationEventQuery("CLIENT001", DeliveryStatus.DELIVERED,
				Instant.parse("2024-03-01T00:00:00Z"), Instant.parse("2024-03-31T00:00:00Z"), 20, 0);
		PagedResult<NotificationEventRecord> page = adapter.search(query);

		assertThat(page.totalElements()).isEqualTo(2);
		assertThat(page.items()).extracting(NotificationEventRecord::eventId).containsExactly("EVT003", "EVT002");
	}

	@Test
	void appliesLimitAndOffset() {
		save("EVT001", "CLIENT003", Instant.parse("2024-03-10T00:00:00Z"), DeliveryStatus.DELIVERED, "ref-1");
		save("EVT002", "CLIENT003", Instant.parse("2024-03-11T00:00:00Z"), DeliveryStatus.DELIVERED, "ref-2");
		save("EVT003", "CLIENT003", Instant.parse("2024-03-12T00:00:00Z"), DeliveryStatus.DELIVERED, "ref-3");

		NotificationEventQuery query = new NotificationEventQuery("CLIENT003", null, null, null, 1, 1);
		PagedResult<NotificationEventRecord> page = adapter.search(query);

		assertThat(page.totalElements()).isEqualTo(3);
		assertThat(page.items()).extracting(NotificationEventRecord::eventId).containsExactly("EVT002");
	}

	private void save(String eventId, String clientId, Instant deliveryDate, DeliveryStatus status,
			String webhookResponse) {
		NotificationEvent event = new NotificationEvent(eventId, "credit_card_payment", "Payment received",
				deliveryDate, clientId);
		recordAdapter.save(event, new DeliveryResult(eventId, status, webhookResponse));
	}

	private UUID idOf(String clientId, String eventId) {
		return adapter.search(new NotificationEventQuery(clientId, null, null, null, 100, 0)).items().stream()
				.filter(record -> record.eventId().equals(eventId))
				.findFirst()
				.orElseThrow()
				.notificationEventId();
	}
}
