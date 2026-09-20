package com.cobre.notification.domain.port.out;

import com.cobre.notification.domain.model.NotificationEvent;

/**
 * A best-effort, shared fast-path cache for duplicate delivery — <strong>not</strong>
 * the source of truth. That's {@link NotificationRecordPort#tryClaim}, an
 * atomic claim against Postgres that's correct on its own regardless of how
 * many instances of this service are running.
 *
 * <p>This port exists purely to skip a Postgres round-trip for the common
 * case of an obvious duplicate (a Kafka at-least-once redelivery, a resend
 * from the platform) — a miss here just falls through to the authoritative
 * claim, so a wrong answer from this cache can only ever cost a redundant
 * DB call, never cause a double delivery. See "Concurrencia y race
 * conditions" in the README for the full picture, including why the
 * production implementation (Redis) being shared across instances also
 * means it correctly short-circuits a duplicate that lands on a
 * <em>different</em> node than the one that first processed it — not
 * required for correctness, but it means fewer of those duplicates ever
 * reach Postgres at all.
 */
public interface IdempotencyPort {

	/**
	 * @return true if this exact event was already successfully delivered
	 *         recently, per this cache. A false negative (cache miss) is
	 *         always safe — {@link NotificationRecordPort#tryClaim} still
	 *         catches it.
	 */
	boolean isDuplicate(NotificationEvent event);

	/**
	 * Records that this event was successfully delivered, so a later
	 * duplicate can be recognized without touching the database. Only call
	 * this after a successful delivery: a failed attempt must remain
	 * eligible for a legitimate retry.
	 */
	void markAsProcessed(NotificationEvent event);
}
