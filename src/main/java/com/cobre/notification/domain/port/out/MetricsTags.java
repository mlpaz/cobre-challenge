package com.cobre.notification.domain.port.out;

/**
 * Tag names used across {@link MetricsPort#increment} calls, kept in one
 * place so every emitter agrees on the same {@code key:value} tag (see the
 * "Métricas" section of the README for the full metric-by-metric tag list).
 */
public enum MetricsTags {

	EVENT_TYPE("event_type"),
	CLIENT_ID("client_id"),
	WEBHOOK("webhook"),
	STATUS_CODE("status_code"),
	DELIVERY_STATUS("delivery_status");

	private final String tag;

	MetricsTags(String tag) {
		this.tag = tag;
	}

	public String of(Object value) {
		return tag + ":" + value;
	}
}
