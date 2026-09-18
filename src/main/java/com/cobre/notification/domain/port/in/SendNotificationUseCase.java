package com.cobre.notification.domain.port.in;

import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.NotificationEvent;

public interface SendNotificationUseCase {

	DeliveryResult sendNotification(NotificationEvent event);
}
