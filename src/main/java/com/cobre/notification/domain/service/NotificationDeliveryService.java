package com.cobre.notification.domain.service;

import org.springframework.stereotype.Service;

import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.in.SendNotificationUseCase;
import com.cobre.notification.domain.port.out.NotificationProviderPort;

@Service
public class NotificationDeliveryService implements SendNotificationUseCase {

	private final NotificationProviderPort notificationProviderPort;

	public NotificationDeliveryService(NotificationProviderPort notificationProviderPort) {
		this.notificationProviderPort = notificationProviderPort;
	}

	@Override
	public DeliveryResult sendNotification(NotificationEvent event) {
		return notificationProviderPort.deliver(event);
	}
}
