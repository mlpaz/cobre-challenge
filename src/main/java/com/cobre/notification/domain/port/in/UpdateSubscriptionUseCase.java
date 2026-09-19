package com.cobre.notification.domain.port.in;

import com.cobre.notification.domain.exception.SubscriptionNotFoundException;
import com.cobre.notification.domain.model.Subscription;

public interface UpdateSubscriptionUseCase {

	/**
	 * Updates the webhook URL of an existing subscription.
	 *
	 * @throws SubscriptionNotFoundException when there is no subscription for this
	 *                                        (userId, eventType) pair — unlike
	 *                                        {@link SubscribeUseCase#subscribe}, this
	 *                                        does not create one.
	 */
	void update(Subscription subscription);
}
