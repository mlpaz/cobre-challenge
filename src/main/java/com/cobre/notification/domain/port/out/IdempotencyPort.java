package com.cobre.notification.domain.port.out;

import com.cobre.notification.domain.model.NotificationEvent;

public interface IdempotencyPort {

	/**
	 * @return true if this exact event was already successfully delivered
	 *         before (within the retention window) — it must be treated as a
	 *         duplicate and skipped, not delivered again.
	 */
	boolean isDuplicate(NotificationEvent event);

	/**
	 * Records that this event was successfully delivered, so a later
	 * duplicate can be recognized. Only call this after a successful
	 * delivery: a failed attempt must remain eligible for a legitimate retry.
	 */
	void markAsProcessed(NotificationEvent event);
}
