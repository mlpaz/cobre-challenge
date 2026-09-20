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
import com.cobre.notification.domain.port.out.NotificationRecordPort;
import com.cobre.notification.domain.port.out.SubscriptionPort;
import com.cobre.notification.domain.port.out.WebhookDeliveryPort;

@Service
public class NotificationEventReplayService implements ReplayNotificationEventUseCase {

	private final NotificationEventQueryPort queryPort;
	private final SubscriptionPort subscriptionPort;
	private final WebhookDeliveryPort webhookDeliveryPort;
	private final NotificationRecordPort notificationRecordPort;
	private final IdempotencyPort idempotencyPort;

	public NotificationEventReplayService(NotificationEventQueryPort queryPort, SubscriptionPort subscriptionPort,
			WebhookDeliveryPort webhookDeliveryPort, NotificationRecordPort notificationRecordPort,
			IdempotencyPort idempotencyPort) {
		this.queryPort = queryPort;
		this.subscriptionPort = subscriptionPort;
		this.webhookDeliveryPort = webhookDeliveryPort;
		this.notificationRecordPort = notificationRecordPort;
		this.idempotencyPort = idempotencyPort;
	}

	@Override
	public DeliveryResult replay(UUID notificationEventId, String userId) {
		NotificationEventRecord record = queryPort.findById(notificationEventId)
				.orElseThrow(() -> new NotificationEventNotFoundException(notificationEventId));
		if (!record.clientId().equals(userId)) {
			throw new NotificationEventAccessDeniedException(notificationEventId, userId);
		}

		// delivery_status is the durable source of truth for "already handled":
		// a replay on an already-delivered event is a no-op instead of calling
		// the webhook again.
		if (record.deliveryStatus() == DeliveryStatus.DELIVERED) {
			return new DeliveryResult(record.eventId(), DeliveryStatus.DUPLICATE, record.webhookResponse());
		}

		NotificationEvent event = new NotificationEvent(record.eventId(), record.eventType(), record.content(),
				record.eventDeliveryDate(), record.clientId());

		Optional<String> webHookUrl = subscriptionPort.findWebHookUrl(event.clientId(), event.eventType());
		if (webHookUrl.isEmpty()) {
			return new DeliveryResult(event.eventId(), DeliveryStatus.NOT_SUBSCRIBED, null);
		}

		// A manual replay is a deliberate, one-off retry: it always attempts
		// delivery regardless of the webhook's circuit breaker state (the
		// outbound adapter never consults isCallPermitted from here), so it can
		// still recover an event even while the automatic flow is
		// short-circuiting that webhook. Its outcome still feeds the score —
		// see WebhookHttpClient — so a run of successful replays can close the
		// circuit again.
		DeliveryResult result;
		try {
			result = webhookDeliveryPort.deliver(event, webHookUrl.get());
			idempotencyPort.markAsProcessed(event);
		} catch (NotificationDeliveryException e) {
			result = new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null);
		}
		notificationRecordPort.save(event, result);
		return result;
	}
}
