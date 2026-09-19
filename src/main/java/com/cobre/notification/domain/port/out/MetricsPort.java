package com.cobre.notification.domain.port.out;

/**
 * Emits operational counters for the notification flow. The domain and the
 * services depend only on this port; which metrics backend actually receives
 * them (Datadog today, potentially something else later) is an adapter
 * concern.
 */
public interface MetricsPort {

	/**
	 * Increments a counter by 1.
	 *
	 * @param metric the metric name (dot-namespaced, e.g. "notification.events.received")
	 * @param tags   key:value tags (e.g. "event_type:credit_card_payment"), as many as apply
	 */
	void increment(String metric, String... tags);
}
