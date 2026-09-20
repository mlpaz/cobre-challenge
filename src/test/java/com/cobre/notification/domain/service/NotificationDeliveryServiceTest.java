package com.cobre.notification.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.NotificationRecordPort;
import com.cobre.notification.domain.port.out.SubscriptionPort;
import com.cobre.notification.domain.port.out.WebhookCircuitBreakerPort;
import com.cobre.notification.domain.port.out.WebhookDeliveryPort;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

	private static final String WEBHOOK_URL = "https://client.example.com/webhooks/notifications";

	@Mock
	private SubscriptionPort subscriptionPort;

	@Mock
	private WebhookDeliveryPort webhookDeliveryPort;

	@Mock
	private NotificationRecordPort notificationRecordPort;

	@Mock
	private MetricsPort metricsPort;

	@Mock
	private WebhookCircuitBreakerPort webhookCircuitBreakerPort;

	@Test
	void delegatesDeliveryToTheOutboundPortAndRecordsTheResultWhenThereIsAnActiveSubscription() {
		NotificationDeliveryService service = newService();
		NotificationEvent event = anEvent();
		DeliveryResult expected = new DeliveryResult(event.eventId(), DeliveryStatus.DELIVERED, "ref-1");
		given(subscriptionPort.findWebHookUrl(event.clientId(), event.eventType())).willReturn(Optional.of(WEBHOOK_URL));
		given(notificationRecordPort.tryClaim(event)).willReturn(true);
		given(webhookCircuitBreakerPort.isCallPermitted(event.clientId(), event.eventType())).willReturn(true);
		given(webhookDeliveryPort.deliver(event, WEBHOOK_URL)).willReturn(expected);

		DeliveryResult result = service.sendNotification(event);

		assertThat(result).isEqualTo(expected);
		verify(webhookDeliveryPort).deliver(event, WEBHOOK_URL);
		verify(notificationRecordPort).complete(event, expected);
		// Recording the outcome against the webhook's circuit breaker score now
		// happens per HTTP attempt inside WebhookHttpClient (so internal retries
		// each count), not once here after deliver() returns.
		verify(webhookCircuitBreakerPort, never()).recordResult(any(), any(), anyBoolean());
		verify(metricsPort).increment("notification.events.received", "event_type:credit_card_payment",
				"client_id:CLIENT001");
		verify(metricsPort).increment("notification.subscription.webhook_found", "event_type:credit_card_payment",
				"client_id:CLIENT001", "webhook:" + WEBHOOK_URL);
		verify(metricsPort).increment("notification.events.saved", "delivery_status:DELIVERED", "client_id:CLIENT001");
	}

	@Test
	void returnsNotSubscribedWithoutCallingTheWebhookWhenThereIsNoWebHookUrl() {
		NotificationDeliveryService service = newService();
		NotificationEvent event = anEvent();
		given(subscriptionPort.findWebHookUrl(event.clientId(), event.eventType())).willReturn(Optional.empty());

		DeliveryResult result = service.sendNotification(event);

		assertThat(result).isEqualTo(new DeliveryResult(event.eventId(), DeliveryStatus.NOT_SUBSCRIBED, null));
		verify(webhookDeliveryPort, never()).deliver(any(), any());
		// No subscription means nothing worth claiming: unlike the old
		// in-memory idempotency cache, the DB claim only ever happens once we
		// know we're actually going to attempt delivery.
		verify(notificationRecordPort, never()).tryClaim(any());
		verify(notificationRecordPort, never()).complete(any(), any());
		verify(metricsPort).increment("notification.subscription.webhook_not_found", "event_type:credit_card_payment",
				"client_id:CLIENT001");
	}

	@Test
	void returnsAndRecordsAFailedResultWhenTheOutboundPortThrowsInsteadOfPropagatingIt() {
		NotificationDeliveryService service = newService();
		NotificationEvent event = anEvent();
		given(subscriptionPort.findWebHookUrl(event.clientId(), event.eventType())).willReturn(Optional.of(WEBHOOK_URL));
		given(notificationRecordPort.tryClaim(event)).willReturn(true);
		given(webhookCircuitBreakerPort.isCallPermitted(event.clientId(), event.eventType())).willReturn(true);
		given(webhookDeliveryPort.deliver(any(), any())).willThrow(new NotificationDeliveryException("boom", null));

		DeliveryResult result = service.sendNotification(event);

		assertThat(result).isEqualTo(new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null));
		verify(notificationRecordPort).complete(event, new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null));
		verify(webhookCircuitBreakerPort, never()).recordResult(any(), any(), anyBoolean());
		verify(metricsPort).increment("notification.events.saved", "delivery_status:FAILED", "client_id:CLIENT001");
	}

	@Test
	void skipsDeliveryAndRecordsCircuitOpenWhenTheWebhookCircuitBreakerDoesNotPermitTheCall() {
		NotificationDeliveryService service = newService();
		NotificationEvent event = anEvent();
		given(subscriptionPort.findWebHookUrl(event.clientId(), event.eventType())).willReturn(Optional.of(WEBHOOK_URL));
		given(notificationRecordPort.tryClaim(event)).willReturn(true);
		given(webhookCircuitBreakerPort.isCallPermitted(event.clientId(), event.eventType())).willReturn(false);

		DeliveryResult result = service.sendNotification(event);

		assertThat(result).isEqualTo(new DeliveryResult(event.eventId(), DeliveryStatus.CIRCUIT_OPEN, null));
		verify(webhookDeliveryPort, never()).deliver(any(), any());
		verify(webhookCircuitBreakerPort, never()).recordResult(any(), any(), anyBoolean());
		verify(notificationRecordPort).complete(event, new DeliveryResult(event.eventId(), DeliveryStatus.CIRCUIT_OPEN, null));
		verify(metricsPort).increment("notification.webhook.delivery_blocked", "event_type:credit_card_payment",
				"client_id:CLIENT001", "webhook:" + WEBHOOK_URL);
		verify(metricsPort).increment("notification.events.saved", "delivery_status:CIRCUIT_OPEN",
				"client_id:CLIENT001");
	}

	@Test
	void returnsADuplicateResultWithoutDeliveringOrCompletingWhenTheClaimIsLost() {
		// A claim is lost either because this exact event was already
		// DELIVERED before, or because a concurrent call (another instance,
		// a Kafka redelivery racing an HTTP retry, ...) is claiming it right
		// now -- notificationRecordPort#tryClaim is the single atomic
		// statement that decides this, not two separate check-then-act steps.
		NotificationDeliveryService service = newService();
		NotificationEvent event = anEvent();
		given(subscriptionPort.findWebHookUrl(event.clientId(), event.eventType())).willReturn(Optional.of(WEBHOOK_URL));
		given(notificationRecordPort.tryClaim(event)).willReturn(false);

		DeliveryResult result = service.sendNotification(event);

		assertThat(result).isEqualTo(new DeliveryResult(event.eventId(), DeliveryStatus.DUPLICATE, null));
		verify(webhookDeliveryPort, never()).deliver(any(), any());
		verify(notificationRecordPort, never()).complete(any(), any());
		verify(metricsPort).increment("notification.events.duplicate", "event_type:credit_card_payment",
				"client_id:CLIENT001");
	}

	private NotificationDeliveryService newService() {
		return new NotificationDeliveryService(subscriptionPort, webhookDeliveryPort, notificationRecordPort,
				metricsPort, webhookCircuitBreakerPort);
	}

	private static NotificationEvent anEvent() {
		return new NotificationEvent("EVT001", "credit_card_payment", "Payment received",
				Instant.parse("2024-03-15T09:30:22Z"), "CLIENT001");
	}
}
