package com.cobre.notification.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import com.cobre.notification.domain.model.NotificationEventQuery;
import com.cobre.notification.domain.model.NotificationEventRecord;
import com.cobre.notification.domain.model.PagedResult;

public interface NotificationEventQueryPort {

	PagedResult<NotificationEventRecord> search(NotificationEventQuery query);

	Optional<NotificationEventRecord> findById(UUID notificationEventId);
}
