package com.cobre.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobre.notification.domain.exception.NotificationEventAccessDeniedException;
import com.cobre.notification.domain.exception.NotificationEventNotFoundException;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEventQuery;
import com.cobre.notification.domain.model.NotificationEventRecord;
import com.cobre.notification.domain.model.PagedResult;
import com.cobre.notification.domain.port.out.NotificationEventQueryPort;

@ExtendWith(MockitoExtension.class)
class NotificationEventQueryServiceTest {

	private static final UUID NOTIFICATION_EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Mock
	private NotificationEventQueryPort queryPort;

	@Test
	void delegatesListingToTheQueryPort() {
		NotificationEventQueryService service = new NotificationEventQueryService(queryPort);
		NotificationEventQuery query = new NotificationEventQuery("CLIENT001", null, null, null, 20, 0);
		PagedResult<NotificationEventRecord> expected = new PagedResult<>(List.of(aRecord()), 1, 20, 0);
		given(queryPort.search(query)).willReturn(expected);

		assertThat(service.listEvents(query)).isEqualTo(expected);
	}

	@Test
	void returnsTheEventWhenTheOwnerMatches() {
		NotificationEventQueryService service = new NotificationEventQueryService(queryPort);
		NotificationEventRecord record = aRecord();
		given(queryPort.findById(NOTIFICATION_EVENT_ID)).willReturn(Optional.of(record));

		assertThat(service.getEvent(NOTIFICATION_EVENT_ID, "CLIENT001")).isEqualTo(record);
	}

	@Test
	void throwsAccessDeniedWhenTheHeaderDoesNotMatchTheOwner() {
		NotificationEventQueryService service = new NotificationEventQueryService(queryPort);
		given(queryPort.findById(NOTIFICATION_EVENT_ID)).willReturn(Optional.of(aRecord()));

		assertThatThrownBy(() -> service.getEvent(NOTIFICATION_EVENT_ID, "OTHER_CLIENT"))
				.isInstanceOf(NotificationEventAccessDeniedException.class);
	}

	@Test
	void throwsNotFoundWhenNoEventExistsWithThatId() {
		NotificationEventQueryService service = new NotificationEventQueryService(queryPort);
		given(queryPort.findById(NOTIFICATION_EVENT_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.getEvent(NOTIFICATION_EVENT_ID, "CLIENT001"))
				.isInstanceOf(NotificationEventNotFoundException.class);
	}

	private static NotificationEventRecord aRecord() {
		return new NotificationEventRecord(NOTIFICATION_EVENT_ID, "EVT001", "credit_card_payment",
				"Payment received", "CLIENT001", Instant.parse("2024-03-15T09:30:22Z"), DeliveryStatus.DELIVERED,
				"ref-123", Instant.parse("2024-03-15T09:31:00Z"));
	}
}
