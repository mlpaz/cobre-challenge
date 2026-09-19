package com.cobre.notification.domain.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cobre.notification.domain.exception.NotificationEventAccessDeniedException;
import com.cobre.notification.domain.exception.NotificationEventNotFoundException;
import com.cobre.notification.domain.model.NotificationEventQuery;
import com.cobre.notification.domain.model.NotificationEventRecord;
import com.cobre.notification.domain.model.PagedResult;
import com.cobre.notification.domain.port.in.QueryNotificationEventsUseCase;
import com.cobre.notification.domain.port.out.NotificationEventQueryPort;

@Service
public class NotificationEventQueryService implements QueryNotificationEventsUseCase {

	private final NotificationEventQueryPort queryPort;

	public NotificationEventQueryService(NotificationEventQueryPort queryPort) {
		this.queryPort = queryPort;
	}

	@Override
	public PagedResult<NotificationEventRecord> listEvents(NotificationEventQuery query) {
		return queryPort.search(query);
	}

	@Override
	public NotificationEventRecord getEvent(UUID notificationEventId, String userId) {
		NotificationEventRecord record = queryPort.findById(notificationEventId)
				.orElseThrow(() -> new NotificationEventNotFoundException(notificationEventId));
		if (!record.clientId().equals(userId)) {
			throw new NotificationEventAccessDeniedException(notificationEventId, userId);
		}
		return record;
	}
}
