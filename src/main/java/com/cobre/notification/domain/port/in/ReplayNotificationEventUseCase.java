package com.cobre.notification.domain.port.in;

import java.util.UUID;

import com.cobre.notification.domain.exception.NotificationEventAccessDeniedException;
import com.cobre.notification.domain.exception.NotificationEventNotFoundException;
import com.cobre.notification.domain.model.DeliveryResult;

public interface ReplayNotificationEventUseCase {

	/**
	 * Re-attempts delivery of a previously recorded event. A no-op (returns
	 * {@link com.cobre.notification.domain.model.DeliveryStatus#DUPLICATE}
	 * without calling the webhook again) if the event is already DELIVERED.
	 *
	 * @throws NotificationEventNotFoundException    when no event exists with this id
	 * @throws NotificationEventAccessDeniedException when the event exists but does not
	 *                                                 belong to {@code userId}
	 */
	DeliveryResult replay(UUID notificationEventId, String userId);
}
