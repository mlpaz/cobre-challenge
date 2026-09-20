package com.cobre.notification.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	/**
	 * Atomic claim for a new/automatic delivery attempt: inserts a fresh
	 * {@code PROCESSING} row, or — on a (client_id, event_id) conflict —
	 * transitions the existing row back to {@code PROCESSING} only if it was
	 * {@code FAILED} or {@code CIRCUIT_OPEN} (a legitimate retry). If the
	 * existing row is {@code DELIVERED}, {@code PROCESSING} (another caller
	 * is already handling it) or anything else, the {@code WHERE} clause
	 * doesn't match, the {@code DO UPDATE} is skipped, and this returns 0 —
	 * this single statement is what makes the claim race-proof: Postgres
	 * resolves the conflict under a row lock, so two concurrent callers for
	 * the same event can never both get back 1.
	 *
	 * @return the number of rows affected: 1 if this call won the claim, 0 otherwise.
	 */
	@Modifying(clearAutomatically = true)
	@Query(value = """
			INSERT INTO notification_events
				(notification_event_id, event_id, client_id, event_type, content, event_delivery_date, delivery_status, processed_at)
			VALUES (:id, :eventId, :clientId, :eventType, :content, :deliveryDate, 'PROCESSING', :now)
			ON CONFLICT (client_id, event_id) DO UPDATE
				SET delivery_status = 'PROCESSING',
					event_type = EXCLUDED.event_type,
					content = EXCLUDED.content,
					event_delivery_date = EXCLUDED.event_delivery_date,
					processed_at = EXCLUDED.processed_at
				WHERE notification_events.delivery_status IN ('FAILED', 'CIRCUIT_OPEN')
			""", nativeQuery = true)
	int tryClaim(@Param("id") UUID id, @Param("eventId") String eventId, @Param("clientId") String clientId,
			@Param("eventType") String eventType, @Param("content") String content,
			@Param("deliveryDate") Instant deliveryDate, @Param("now") Instant now);

	/**
	 * Atomic claim for a manual replay of an existing row: same idea as
	 * {@link #tryClaim}, but keyed by id (the row already exists) and never
	 * inserts.
	 *
	 * @return the number of rows affected: 1 if this call won the claim, 0 otherwise.
	 */
	@Modifying(clearAutomatically = true)
	@Query(value = """
			UPDATE notification_events
			SET delivery_status = 'PROCESSING'
			WHERE notification_event_id = :id
			AND delivery_status IN ('FAILED', 'CIRCUIT_OPEN')
			""", nativeQuery = true)
	int tryClaimForReplay(@Param("id") UUID id);

	/** Writes the final outcome of a claimed (client_id, event_id) row, moving it out of PROCESSING. */
	@Modifying(clearAutomatically = true)
	@Query(value = """
			UPDATE notification_events
			SET delivery_status = :status, webhook_response = :webhookResponse, processed_at = :processedAt
			WHERE client_id = :clientId AND event_id = :eventId
			""", nativeQuery = true)
	int complete(@Param("clientId") String clientId, @Param("eventId") String eventId,
			@Param("status") String status, @Param("webhookResponse") String webhookResponse,
			@Param("processedAt") Instant processedAt);

	/**
	 * Recovery sweep: a row stuck in {@code PROCESSING} past {@code threshold}
	 * means the process that claimed it died before calling {@link #complete}.
	 * Flipping it to {@code FAILED} makes it replayable again through the
	 * normal self-service {@code POST /notification_events/{id}/replay}
	 * instead of staying claimed forever.
	 *
	 * @return how many stuck rows were recovered.
	 */
	@Modifying(clearAutomatically = true)
	@Query(value = """
			UPDATE notification_events
			SET delivery_status = 'FAILED'
			WHERE delivery_status = 'PROCESSING' AND processed_at < :threshold
			""", nativeQuery = true)
	int failStuckProcessing(@Param("threshold") Instant threshold);
}
