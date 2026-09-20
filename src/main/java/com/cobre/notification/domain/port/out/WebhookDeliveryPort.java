package com.cobre.notification.domain.port.out;

import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.NotificationEvent;

public interface WebhookDeliveryPort {

	/**
	 * Delivers the event directly to the given webhook URL over HTTP.
	 *
	 * @throws NotificationDeliveryException when the webhook rejects the event,
	 *                                        or stays unavailable after the retry
	 *                                        strategy is exhausted.
	 */
	DeliveryResult deliver(NotificationEvent event, String webHookUrl);
}
