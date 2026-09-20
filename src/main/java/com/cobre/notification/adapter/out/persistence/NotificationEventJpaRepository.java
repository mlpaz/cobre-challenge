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
			AND (CAST(:status AS varchar) IS NULL OR e.delivery_status = CAST(:status AS varchar))
			AND (CAST(:from AS timestamp with time zone) IS NULL OR e.event_delivery_date >= CAST(:from AS timestamp with time zone))
			AND (CAST(:to AS timestamp with time zone) IS NULL OR e.event_delivery_date <= CAST(:to AS timestamp with time zone))
			ORDER BY e.event_delivery_date DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<NotificationEventEntity> search(@Param("clientId") String clientId, @Param("status") String status,
			@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit,
			@Param("offset") int offset);

	@Query(value = """
			SELECT COUNT(*) FROM notification_events e
			WHERE e.client_id = :clientId
			AND (CAST(:status AS varchar) IS NULL OR e.delivery_status = CAST(:status AS varchar))
			AND (CAST(:from AS timestamp with time zone) IS NULL OR e.event_delivery_date >= CAST(:from AS timestamp with time zone))
			AND (CAST(:to AS timestamp with time zone) IS NULL OR e.event_delivery_date <= CAST(:to AS timestamp with time zone))
			""", nativeQuery = true)
	long countSearch(@Param("clientId") String clientId, @Param("status") String status, @Param("from") Instant from,
			@Param("to") Instant to);
}
