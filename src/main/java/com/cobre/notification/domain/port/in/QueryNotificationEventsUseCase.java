package com.cobre.notification.domain.port.in;

import java.util.UUID;

import com.cobre.notification.domain.exception.NotificationEventAccessDeniedException;
import com.cobre.notification.domain.exception.NotificationEventNotFoundException;
import com.cobre.notification.domain.model.NotificationEventQuery;
import com.cobre.notification.domain.model.NotificationEventRecord;
import com.cobre.notification.domain.model.PagedResult;

public interface QueryNotificationEventsUseCase {

	PagedResult<NotificationEventRecord> listEvents(NotificationEventQuery query);

	/**
	 * @throws NotificationEventNotFoundException   when no event exists with this id
	 * @throws NotificationEventAccessDeniedException when the event exists but does not
	 *                                                belong to {@code userId}
	 */
	NotificationEventRecord getEvent(UUID notificationEventId, String userId);
}
