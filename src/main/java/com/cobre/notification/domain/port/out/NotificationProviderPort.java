package com.cobre.notification.domain.port.out;

import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.NotificationEvent;

public interface NotificationProviderPort {

	/**
	 * Delivers the event to the Notification Provider, telling it which
	 * webhook URL to call.
	 *
	 * @throws NotificationDeliveryException when the provider rejects the event,
	 *                                        or stays unavailable after the retry
	 *                                        strategy is exhausted.
	 */
	DeliveryResult deliver(NotificationEvent event, String webHookUrl);
}
