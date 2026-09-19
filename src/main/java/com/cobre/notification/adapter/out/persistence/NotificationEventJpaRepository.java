package com.cobre.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationEventJpaRepository extends JpaRepository<NotificationEventEntity, UUID> {

	Optional<NotificationEventEntity> findByClientIdAndEventId(String clientId, String eventId);

	@Query(value = """
			SELECT * FROM notification_events e
			WHERE e.client_id = :clientId
			AND (:status IS NULL OR e.delivery_status = :status)
			AND (:from IS NULL OR e.event_delivery_date >= :from)
			AND (:to IS NULL OR e.event_delivery_date <= :to)
			ORDER BY e.event_delivery_date DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<NotificationEventEntity> search(@Param("clientId") String clientId, @Param("status") String status,
			@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit,
			@Param("offset") int offset);

	@Query(value = """
			SELECT COUNT(*) FROM notification_events e
			WHERE e.client_id = :clientId
			AND (:status IS NULL OR e.delivery_status = :status)
			AND (:from IS NULL OR e.event_delivery_date >= :from)
			AND (:to IS NULL OR e.event_delivery_date <= :to)
			""", nativeQuery = true)
	long countSearch(@Param("clientId") String clientId, @Param("status") String status, @Param("from") Instant from,
			@Param("to") Instant to);
}
