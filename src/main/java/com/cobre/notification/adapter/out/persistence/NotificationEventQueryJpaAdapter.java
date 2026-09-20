package com.cobre.notification.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cobre.notification.domain.model.NotificationEventQuery;
import com.cobre.notification.domain.model.NotificationEventRecord;
import com.cobre.notification.domain.model.PagedResult;
import com.cobre.notification.domain.port.out.NotificationEventQueryPort;

@Component
public class NotificationEventQueryJpaAdapter implements NotificationEventQueryPort {

	private final NotificationEventJpaRepository repository;

	public NotificationEventQueryJpaAdapter(NotificationEventJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public PagedResult<NotificationEventRecord> search(NotificationEventQuery query) {
		String status = query.deliveryStatus() == null ? null : query.deliveryStatus().name();
		List<NotificationEventRecord> items = repository
				.search(query.clientId(), status, query.createdFrom(), query.createdTo(), query.limit(),
						query.offset())
				.stream()
				.map(NotificationEventQueryJpaAdapter::toRecord)
				.toList();
		long total = repository.countSearch(query.clientId(), status, query.createdFrom(), query.createdTo());
		return new PagedResult<>(items, total, query.limit(), query.offset());
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<NotificationEventRecord> findById(UUID notificationEventId) {
		return repository.findById(notificationEventId).map(NotificationEventQueryJpaAdapter::toRecord);
	}

	private static NotificationEventRecord toRecord(NotificationEventEntity entity) {
		return new NotificationEventRecord(
				entity.getNotificationEventId(),
				entity.getEventId(),
				entity.getEventType(),
				entity.getContent(),
				entity.getClientId(),
				entity.getEventDeliveryDate(),
				entity.getDeliveryStatus(),
				entity.getWebhookResponse(),
				entity.getProcessedAt());
	}
}
