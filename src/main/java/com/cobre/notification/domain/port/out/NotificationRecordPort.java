package com.cobre.notification.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.NotificationEvent;
import com.cobre.notification.domain.model.RecoveredStuckEvent;

public interface NotificationRecordPort {

	/**
	 * Atomically claims this event for processing: inserts a new
	 * {@code PROCESSING} row for this (client_id, event_id), or — if a row
	 * already exists and is in a legitimately retryable state
	 * ({@code FAILED} or {@code CIRCUIT_OPEN}) — transitions it back to
	 * {@code PROCESSING}. Both cases are a single atomic statement, so two
	 * concurrent callers for the exact same event can never both win.
	 *
	 * @return true if this caller won the claim and must now attempt
	 *         delivery and call {@link #complete}; false if the event is
	 *         already {@code DELIVERED}, or another caller is claiming it
	 *         right now — either way, the webhook must not be called again.
	 */
	boolean tryClaim(NotificationEvent event);

	/**
	 * Same idea as {@link #tryClaim}, for a manual replay of an existing,
	 * already-persisted row: atomically transitions it from a retryable
	 * status ({@code FAILED}/{@code CIRCUIT_OPEN}) to {@code PROCESSING}.
	 *
	 * @return true if this caller won the claim.
	 */
	boolean tryClaimForReplay(UUID notificationEventId);

	/**
	 * Persists the final outcome of a claimed event, moving it out of
	 * {@code PROCESSING}. Only call this after a successful {@link #tryClaim}
	 * or {@link #tryClaimForReplay} — it updates an existing row, it never
	 * creates one.
	 */
	void complete(NotificationEvent event, DeliveryResult result);

	/**
	 * Recovers rows stuck in {@code PROCESSING} since before {@code olderThan}
	 * by flipping them to {@code FAILED}, so they become replayable again
	 * instead of staying claimed forever — see the scheduled job that calls
	 * this.
	 *
	 * @return the identity (client_id, event_type) of every row recovered,
	 *         one entry per row — used to tag the recovery metric per event.
	 */
	List<RecoveredStuckEvent> recoverStuckProcessing(Instant olderThan);
}
