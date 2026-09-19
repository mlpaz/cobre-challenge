package com.cobre.notification.domain.port.out;

import java.util.Optional;

import com.cobre.notification.domain.model.Subscription;

public interface SubscriptionPort {

	/**
	 * @return the webhook URL subscribed for this (user, event type) pair, or
	 *         empty if there is none — there is nowhere to deliver this event.
	 */
	Optional<String> findWebHookUrl(String userId, String eventType);

	/**
	 * Creates a subscription, or updates the webhook URL if one already
	 * exists for this (user, event type) pair.
	 */
	void save(Subscription subscription);
}
