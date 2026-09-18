package com.cobre.notification.domain.port.out;

import com.cobre.notification.domain.exception.SubscriptionCheckException;
import com.cobre.notification.domain.model.NotificationEvent;

public interface SubscriptionPort {

	/**
	 * Confirms, against the subscription registry, that {@code event} must be
	 * delivered. A subscription is keyed by client and event type, so a
	 * positive answer also confirms the event actually belongs to that
	 * client — a client can never be subscribed to another client's events.
	 *
	 * @throws SubscriptionCheckException when the subscription registry
	 *                                     cannot be reached to confirm it.
	 */
	boolean isSubscribed(NotificationEvent event);
}
