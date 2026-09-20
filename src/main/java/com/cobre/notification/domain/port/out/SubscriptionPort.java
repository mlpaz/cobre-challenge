package com.cobre.notification.domain.port.out;

import java.util.List;
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

	/** @return every subscription for this user, one per event type. */
	List<Subscription> findByUserId(String userId);

	/** Deletes the subscription for this (user, event type) pair, if one exists. */
	void delete(String userId, String eventType);
}
