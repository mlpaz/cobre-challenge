package com.cobre.notification.adapter.out.persistence;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.port.out.NotificationRecordPort;

@Component
public class NotificationRecordJpaAdapter implements NotificationRecordPort {

	private final NotificationEventJpaRepository repository;

	public NotificationRecordJpaAdapter(NotificationEventJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public void save(NotificationEvent event, DeliveryResult result) {
		Instant processedAt = Instant.now();
		repository.findByClientIdAndEventId(event.clientId(), event.eventId())
				.ifPresentOrElse(
						existing -> existing.applyResult(event, result, processedAt),
						() -> repository.save(new NotificationEventEntity(event, result, processedAt)));
	}
}
