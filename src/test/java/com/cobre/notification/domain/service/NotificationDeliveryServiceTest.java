package com.cobre.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.exception.SubscriptionNotConfirmedException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.out.IdempotencyPort;
import com.cobre.notification.domain.port.out.NotificationProviderPort;
import com.cobre.notification.domain.port.out.NotificationRecordPort;
import com.cobre.notification.domain.port.out.SubscriptionPort;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

	@Mock
	private IdempotencyPort idempotencyPort;

	@Mock
	private SubscriptionPort subscriptionPort;

	@Mock
	private NotificationProviderPort notificationProviderPort;

	@Mock
	private NotificationRecordPort notificationRecordPort;

	@Test
	void delegatesDeliveryToTheOutboundPortAndRecordsTheResultWhenTheSubscriptionIsConfirmed() {
		NotificationDeliveryService service = newService();
		NotificationEvent event = anEvent();
		DeliveryResult expected = new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, "ref-1");
		given(idempotencyPort.isDuplicate(event)).willReturn(false);
		given(subscriptionPort.isSubscribed(event)).willReturn(true);
		given(notificationProviderPort.deliver(event)).willReturn(expected);

		DeliveryResult result = service.sendNotification(event);

		assertThat(result).isEqualTo(expected);
		verify(notificationProviderPort).deliver(event);
		verify(idempotencyPort).markAsProcessed(event);
		verify(notificationRecordPort).save(event, expected);
	}

	@Test
	void recordsAndRejectsTheEventWithoutCallingTheProviderWhenThereIsNoActiveSubscription() {
		NotificationDeliveryService service = newService();
		NotificationEvent event = anEvent();
		given(idempotencyPort.isDuplicate(event)).willReturn(false);
		given(subscriptionPort.isSubscribed(event)).willReturn(false);

		assertThatThrownBy(() -> service.sendNotification(event))
				.isInstanceOf(SubscriptionNotConfirmedException.class);
		verify(notificationProviderPort, never()).deliver(any());
		verify(idempotencyPort, never()).markAsProcessed(any());
		verify(notificationRecordPort).save(event, new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null));
	}

	@Test
	void recordsAndPropagatesDeliveryExceptionsFromTheOutboundPortAndDoesNotMarkItAsProcessed() {
		NotificationDeliveryService service = newService();
		NotificationEvent event = anEvent();
		given(idempotencyPort.isDuplicate(event)).willReturn(false);
		given(subscriptionPort.isSubscribed(event)).willReturn(true);
		given(notificationProviderPort.deliver(any())).willThrow(new NotificationDeliveryException("boom", null));

		assertThatThrownBy(() -> service.sendNotification(event))
				.isInstanceOf(NotificationDeliveryException.class)
				.hasMessageContaining("boom");
		verify(idempotencyPort, never()).markAsProcessed(any());
		verify(notificationRecordPort).save(event, new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null));
	}

	@Test
	void returnsADuplicateResultWithoutCheckingSubscriptionDeliveringOrRecordingItAgain() {
		NotificationDeliveryService service = newService();
		NotificationEvent event = anEvent();
		given(idempotencyPort.isDuplicate(event)).willReturn(true);

		DeliveryResult result = service.sendNotification(event);

		assertThat(result).isEqualTo(new DeliveryResult(event.eventId(), DeliveryStatus.DUPLICATE, null));
		verify(subscriptionPort, never()).isSubscribed(any());
		verify(notificationProviderPort, never()).deliver(any());
		verify(notificationRecordPort, never()).save(any(), any());
	}

	private NotificationDeliveryService newService() {
		return new NotificationDeliveryService(idempotencyPort, subscriptionPort, notificationProviderPort,
				notificationRecordPort);
	}

	private static NotificationEvent anEvent() {
		return new NotificationEvent("EVT001", "credit_card_payment", "Payment received",
				Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001");
	}
}
