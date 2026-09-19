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

@Service
public class NotificationEventReplayService implements ReplayNotificationEventUseCase {

	private final NotificationEventQueryPort queryPort;
	private final SubscriptionPort subscriptionPort;
	private final NotificationProviderPort notificationProviderPort;
	private final NotificationRecordPort notificationRecordPort;
	private final IdempotencyPort idempotencyPort;

	public NotificationEventReplayService(NotificationEventQueryPort queryPort, SubscriptionPort subscriptionPort,
			NotificationProviderPort notificationProviderPort, NotificationRecordPort notificationRecordPort,
			IdempotencyPort idempotencyPort) {
		this.queryPort = queryPort;
		this.subscriptionPort = subscriptionPort;
		this.notificationProviderPort = notificationProviderPort;
		this.notificationRecordPort = notificationRecordPort;
		this.idempotencyPort = idempotencyPort;
	}

	@Override
	public DeliveryResult replay(UUID notificationEventId, String userId) {
		NotificationEventRecord record = queryPort.findById(notificationEventId)
				.orElseThrow(() -> new NotificationEventNotFoundException(notificationEventId));
		if (!record.clientId().equals(userId)) {
			throw new NotificationEventAccessDeniedException(
					"The notification event does not belong to the requesting user");
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

		DeliveryResult result;
		try {
			result = notificationProviderPort.deliver(event, webHookUrl.get());
			idempotencyPort.markAsProcessed(event);
		} catch (NotificationDeliveryException e) {
			result = new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null);
		}
		notificationRecordPort.save(event, result);
		return result;
	}
}
