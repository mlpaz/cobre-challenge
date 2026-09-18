package com.cobre.notification.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEventEntity, UUID> {

	Optional<NotificationEventEntity> findByClientIdAndEventId(String clientId, String eventId);
}
