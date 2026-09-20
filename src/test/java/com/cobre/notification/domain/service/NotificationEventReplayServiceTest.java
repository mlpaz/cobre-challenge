package com.cobre.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.exception.NotificationEventAccessDeniedException;
import com.cobre.notification.domain.exception.NotificationEventNotFoundException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEventRecord;
import com.cobre.notification.domain.port.out.NotificationEventQueryPort;
import com.cobre.notification.domain.port.out.NotificationRecordPort;
import com.cobre.notification.domain.port.out.SubscriptionPort;
import com.cobre.notification.domain.port.out.WebhookDeliveryPort;

@ExtendWith(MockitoExtension.class)
class NotificationEventReplayServiceTest {

	private static final String WEBHOOK_URL = "https://client.example.com/webhooks/notifications";
	private static final UUID NOTIFICATION_EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Mock
	private NotificationEventQueryPort queryPort;

	@Mock
	private SubscriptionPort subscriptionPort;

	@Mock
	private WebhookDeliveryPort webhookDeliveryPort;

	@Mock
	private NotificationRecordPort notificationRecordPort;

	@Test
	void redeliversAFailedEventAndRecordsTheNewResult() {
		NotificationEventReplayService service = newService();
		NotificationEventRecord record = aRecord(DeliveryStatus.FAILED, null);
		given(queryPort.findById(NOTIFICATION_EVENT_ID)).willReturn(Optional.of(record));
		given(notificationRecordPort.tryClaimForReplay(NOTIFICATION_EVENT_ID)).willReturn(true);
		given(subscriptionPort.findWebHookUrl("CLIENT001", "credit_card_payment")).willReturn(Optional.of(WEBHOOK_URL));
		DeliveryResult expected = new DeliveryResult("EVT001", DeliveryStatus.DELIVERED, "ref-new");
		given(webhookDeliveryPort.deliver(any(), any())).willReturn(expected);

		DeliveryResult result = service.replay(NOTIFICATION_EVENT_ID, "CLIENT001");

		assertThat(result).isEqualTo(expected);
		verify(notificationRecordPort).complete(any(), any());
	}

	@Test
	void returnsDuplicateWithoutCallingTheWebhookWhenAConcurrentAttemptAlreadyWonTheClaim() {
		// Two simultaneous replays of the same event, or a replay racing an
		// automatic redelivery of the same event, must not both reach the
		// webhook -- tryClaimForReplay is the single atomic statement that
		// decides who wins.
		NotificationEventReplayService service = newService();
		NotificationEventRecord record = aRecord(DeliveryStatus.FAILED, null);
		given(queryPort.findById(NOTIFICATION_EVENT_ID)).willReturn(Optional.of(record));
		given(notificationRecordPort.tryClaimForReplay(NOTIFICATION_EVENT_ID)).willReturn(false);

		DeliveryResult result = service.replay(NOTIFICATION_EVENT_ID, "CLIENT001");

		assertThat(result).isEqualTo(new DeliveryResult("EVT001", DeliveryStatus.DUPLICATE, null));
		verify(webhookDeliveryPort, never()).deliver(any(), any());
		verify(notificationRecordPort, never()).complete(any(), any());
	}

	@Test
	void doesNotContactTheWebhookAgainWhenTheEventIsAlreadyDelivered() {
		NotificationEventReplayService service = newService();
		NotificationEventRecord record = aRecord(DeliveryStatus.DELIVERED, "ref-old");
		given(queryPort.findById(NOTIFICATION_EVENT_ID)).willReturn(Optional.of(record));

		DeliveryResult result = service.replay(NOTIFICATION_EVENT_ID, "CLIENT001");

		assertThat(result).isEqualTo(new DeliveryResult("EVT001", DeliveryStatus.DUPLICATE, "ref-old"));
		verify(webhookDeliveryPort, never()).deliver(any(), any());
		verify(notificationRecordPort, never()).tryClaimForReplay(any());
		verify(notificationRecordPort, never()).complete(any(), any());
	}

	@Test
	void recordsAFailedResultWhenTheWebhookRejectsTheReplay() {
		NotificationEventReplayService service = newService();
		NotificationEventRecord record = aRecord(DeliveryStatus.FAILED, null);
		given(queryPort.findById(NOTIFICATION_EVENT_ID)).willReturn(Optional.of(record));
		given(notificationRecordPort.tryClaimForReplay(NOTIFICATION_EVENT_ID)).willReturn(true);
		given(subscriptionPort.findWebHookUrl("CLIENT001", "credit_card_payment")).willReturn(Optional.of(WEBHOOK_URL));
		given(webhookDeliveryPort.deliver(any(), any())).willThrow(new NotificationDeliveryException("boom", null));

		DeliveryResult result = service.replay(NOTIFICATION_EVENT_ID, "CLIENT001");

		assertThat(result).isEqualTo(new DeliveryResult("EVT001", DeliveryStatus.FAILED, null));
		verify(notificationRecordPort).complete(any(), any());
	}

	@Test
	void resolvesTheClaimAsNotSubscribedWhenTheSubscriptionWasRemovedSinceTheOriginalAttempt() {
		NotificationEventReplayService service = newService();
		NotificationEventRecord record = aRecord(DeliveryStatus.FAILED, null);
		given(queryPort.findById(NOTIFICATION_EVENT_ID)).willReturn(Optional.of(record));
		given(notificationRecordPort.tryClaimForReplay(NOTIFICATION_EVENT_ID)).willReturn(true);
		given(subscriptionPort.findWebHookUrl("CLIENT001", "credit_card_payment")).willReturn(Optional.empty());

		DeliveryResult result = service.replay(NOTIFICATION_EVENT_ID, "CLIENT001");

		assertThat(result).isEqualTo(new DeliveryResult("EVT001", DeliveryStatus.NOT_SUBSCRIBED, null));
		verify(webhookDeliveryPort, never()).deliver(any(), any());
		// The claim already moved this row to PROCESSING, so it must still be
		// resolved -- leaving it claimed and unresolved is exactly the stuck-row
		// bug the claim/complete discipline exists to avoid.
		verify(notificationRecordPort).complete(any(), any());
	}

	@Test
	void throwsWhenTheEventDoesNotExist() {
		NotificationEventReplayService service = newService();
		given(queryPort.findById(NOTIFICATION_EVENT_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.replay(NOTIFICATION_EVENT_ID, "CLIENT001"))
				.isInstanceOf(NotificationEventNotFoundException.class);
	}

	@Test
	void throwsWhenTheUserDoesNotOwnTheEvent() {
		NotificationEventReplayService service = newService();
		NotificationEventRecord record = aRecord(DeliveryStatus.FAILED, null);
		given(queryPort.findById(NOTIFICATION_EVENT_ID)).willReturn(Optional.of(record));

		assertThatThrownBy(() -> service.replay(NOTIFICATION_EVENT_ID, "OTHER_CLIENT"))
				.isInstanceOf(NotificationEventAccessDeniedException.class);
		verify(webhookDeliveryPort, never()).deliver(any(), any());
	}

	private NotificationEventReplayService newService() {
		return new NotificationEventReplayService(queryPort, subscriptionPort, webhookDeliveryPort,
				notificationRecordPort);
	}

	private static NotificationEventRecord aRecord(DeliveryStatus status, String webhookResponse) {
		return new NotificationEventRecord(NOTIFICATION_EVENT_ID, "EVT001", "credit_card_payment",
				"Payment received", "CLIENT001", Instant.parse("2024-03-15T09:30:22Z"), status, webhookResponse,
				Instant.parse("2024-03-15T09:31:00Z"));
	}
}
