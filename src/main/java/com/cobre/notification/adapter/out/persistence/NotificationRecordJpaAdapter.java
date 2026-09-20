package com.cobre.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

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
	public boolean tryClaim(NotificationEvent event) {
		int rows = repository.tryClaim(UUID.randomUUID(), event.eventId(), event.clientId(), event.eventType(),
				event.content(), event.deliveryDate(), Instant.now());
		return rows > 0;
	}

	@Override
	@Transactional
	public boolean tryClaimForReplay(UUID notificationEventId) {
		return repository.tryClaimForReplay(notificationEventId) > 0;
	}

	@Override
	@Transactional
	public void complete(NotificationEvent event, DeliveryResult result) {
		repository.complete(event.clientId(), event.eventId(), result.status().name(), result.webhookResponse(),
				Instant.now());
	}

	@Override
	@Transactional
	public int recoverStuckProcessing(Instant olderThan) {
		return repository.failStuckProcessing(olderThan);
	}
}
