package com.cobre.notification.domain.port.out;

import com.cobre.notification.domain.exception.NotificationDeliveryException;
import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.NotificationEvent;

public interface NotificationProviderPort {

	/**
	 * Delivers the event to the Notification Provider.
	 *
	 * @throws NotificationDeliveryException when the provider rejects the event,
	 *                                        stays unavailable after the retry
	 *                                        strategy is exhausted, or the circuit
	 *                                        breaker is open.
	 */
	DeliveryResult deliver(NotificationEvent event);
}
