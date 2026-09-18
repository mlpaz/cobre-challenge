package com.cobre.notification.domain.port.out;

import com.cobre.notification.domain.model.DeliveryResult;
import com.cobre.notification.domain.model.NotificationEvent;

public interface NotificationRecordPort {

	/**
	 * Persists the final outcome of processing this event. Keyed by
	 * (client_id, event_id): a later call for the same event (e.g. after a
	 * replay) updates the existing record instead of adding a new one, so
	 * this table always holds the current, final state per event.
	 */
	void save(NotificationEvent event, DeliveryResult result);
}
