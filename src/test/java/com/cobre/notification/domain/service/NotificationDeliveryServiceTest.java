package com.cobre.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.out.NotificationProviderPort;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

	@Mock
	private NotificationProviderPort notificationProviderPort;

	@Test
	void delegatesDeliveryToTheOutboundPortAndReturnsItsResult() {
		NotificationDeliveryService service = new NotificationDeliveryService(notificationProviderPort);
		NotificationEvent event = anEvent();
		DeliveryResult expected = new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, "ref-1");
		given(notificationProviderPort.deliver(event)).willReturn(expected);

		DeliveryResult result = service.sendNotification(event);

		assertThat(result).isEqualTo(expected);
		verify(notificationProviderPort).deliver(event);
	}

	@Test
	void propagatesDeliveryExceptionsFromTheOutboundPort() {
		NotificationDeliveryService service = new NotificationDeliveryService(notificationProviderPort);
		NotificationEvent event = anEvent();
		given(notificationProviderPort.deliver(any())).willThrow(new NotificationDeliveryException("boom", null));

		assertThatThrownBy(() -> service.sendNotification(event))
				.isInstanceOf(NotificationDeliveryException.class)
				.hasMessageContaining("boom");
	}

	private static NotificationEvent anEvent() {
		return new NotificationEvent("EVT001", "credit_card_payment", "Payment received",
				Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001");
	}
}
