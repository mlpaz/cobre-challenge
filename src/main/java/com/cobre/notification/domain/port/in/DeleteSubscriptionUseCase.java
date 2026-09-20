package com.cobre.notification.domain.port.in;

import com.cobre.notification.domain.exception.SubscriptionNotFoundException;

public interface DeleteSubscriptionUseCase {

	/**
	 * Deletes the subscription for this (userId, eventType) pair.
	 *
	 * @throws SubscriptionNotFoundException when there is no subscription for this pair
	 */
	void delete(String userId, String eventType);
}
