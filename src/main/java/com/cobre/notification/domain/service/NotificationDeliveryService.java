package com.cobre.notification.domain.service;

import org.springframework.stereotype.Service;

import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.exception.SubscriptionNotConfirmedException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.DeliveryStatus;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.in.SendNotificationUseCase;
import com.cobre.notification.domain.port.out.IdempotencyPort;
import com.cobre.notification.domain.port.out.NotificationProviderPort;
import com.cobre.notification.domain.port.out.NotificationRecordPort;
import com.cobre.notification.domain.port.out.SubscriptionPort;

@Service
public class NotificationDeliveryService implements SendNotificationUseCase {

	private final IdempotencyPort idempotencyPort;
	private final SubscriptionPort subscriptionPort;
	private final NotificationProviderPort notificationProviderPort;
	private final NotificationRecordPort notificationRecordPort;

	public NotificationDeliveryService(IdempotencyPort idempotencyPort, SubscriptionPort subscriptionPort,
			NotificationProviderPort notificationProviderPort, NotificationRecordPort notificationRecordPort) {
		this.idempotencyPort = idempotencyPort;
		this.subscriptionPort = subscriptionPort;
		this.notificationProviderPort = notificationProviderPort;
		this.notificationRecordPort = notificationRecordPort;
	}

	@Override
	public DeliveryResult sendNotification(NotificationEvent event) {
		if (idempotencyPort.isDuplicate(event)) {
			return new DeliveryResult(event.eventId(), DeliveryStatus.DUPLICATE, null);
		}
		if (!subscriptionPort.isSubscribed(event)) {
			notificationRecordPort.save(event, new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null));
			throw new SubscriptionNotConfirmedException(
					"No active subscription confirms that event " + event.eventId() + " of type "
							+ event.eventType() + " belongs to client " + event.clientId());
		}
		try {
			DeliveryResult result = notificationProviderPort.deliver(event);
			idempotencyPort.markAsProcessed(event);
			notificationRecordPort.save(event, result);
			return result;
		} catch (NotificationDeliveryException e) {
			notificationRecordPort.save(event, new DeliveryResult(event.eventId(), DeliveryStatus.FAILED, null));
			throw e;
		}
	}
}
