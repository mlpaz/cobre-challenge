package com.cobre.notification.domain.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.exception.NotificationEventAccessDeniedException;
import com.cobre.notification.domain.exception.NotificationEventNotFoundException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.model.NotificationEventRecord;
import com.cobre.notification.domain.port.in.ReplayNotificationEventUseCase;
import com.cobre.notification.domain.port.out.IdempotencyPort;
import com.cobre.notification.domain.port.out.NotificationEventQueryPort;
import com.cobre.notification.domain.port.out.NotificationProviderPort;
import com.cobre.notification.domain.port.out.NotificationRecordPort;
import com.cobre.notification.domain.port.out.SubscriptionPort;
import com.cobre.notification.domain.port.out.WebhookCircuitBreakerPort;

@Service
public class NotificationEventReplayService implements ReplayNotificationEventUseCase {

	private final NotificationEventQueryPort queryPort;
	private final SubscriptionPort subscriptionPort;
	private final NotificationProviderPort notificationProviderPort;
	private final NotificationRecordPort notificationRecordPort;
	private final IdempotencyPort idempotencyPort;
	private final WebhookCircuitBreakerPort webhookCircuitBreakerPort;

	public NotificationEventReplayService(NotificationEventQueryPort queryPort, SubscriptionPort subscriptionPort,
			NotificationProviderPort notificationProviderPort, NotificationRecordPort notificationRecordPort,
			IdempotencyPort idempotencyPort, WebhookCircuitBreakerPort webhookCircuitBreakerPort) {
		this.queryPort = queryPort;
		this.subscriptionPort = subscriptionPort;
		this.notificationProviderPort = notificationProviderPort;
		this.notificationRecordPort = notificationRecordPort;
		this.idempotencyPort = idempotencyPort;
		this.webhookCircuitBreakerPort = webhookCircuitBreakerPort;
	}

	@Override
	public DeliveryResult replay(UUID notificationEventId, String userId) {
		NotificationEventRecord record = queryPort.findById(notificationEventId)
				.orElseThrow(() -> new NotificationEventNotFoundException(notificationEventId));
		if (!record.clientId().equals(userId)) {
			throw new NotificationEventAccessDeniedException(notificationEventId, userId);
		}

		// delivery_status is the durable source of truth for "already handled":
		// a replay on an already-delivered event is a no-op instead of
		// notifying the provider again.
		if (record.deliveryStatus() == DeliveryStatus.DELIVERED) {
			return new DeliveryResult(record.eventId(), DeliveryStatus.DUPLICATE, record.providerReference());
		}

		NotificationEvent event = new NotificationEvent(record.eventId(), record.eventType(), record.content(),
				record.eventDeliveryDate(), record.clientId());

		Optional<String> webHookUrl = subscriptionPort.findWebHookUrl(event.clientId(), event.eventType());
		if (webHookUrl.isEmpty()) {
			return new DeliveryResult(event.eventId(), DeliveryStatus.NOT_SUBSCRIBED, null);
		}

		// A manual replay is a deliberate, one-off retry: it always attempts
		// delivery regardless of the webhook's circuit breaker state, so it can
		// still recover an event even while the automatic flow is short-circuiting
		// that webhook. Its outcome still feeds the score, so a run of successful
		// replays can close the circuit again.
		DeliveryResult result;
		try {
			result = notificationProviderPort.deliver(event, webHookUrl.get());
			idempotencyPort.markAsProcessed(event);
			webhookCircuitBreakerPort.recordResult(event.clientId(), event.eventType(), true);
		} catch (NotificationDeliveryException e) {
			result = new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null);
			webhookCircuitBreakerPort.recordResult(event.clientId(), event.eventType(), false);
		}
		notificationRecordPort.save(event, result);
		return result;
	}
}
