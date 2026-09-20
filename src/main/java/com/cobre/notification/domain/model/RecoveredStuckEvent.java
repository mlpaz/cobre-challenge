package com.cobre.notification.domain.model;

/**
 * One row flipped from {@code PROCESSING} to {@code FAILED} by the stuck-
 * processing recovery sweep — just enough identity to tag the
 * {@code notification.events.stuck_processing_recovered} metric per event.
 */
public record RecoveredStuckEvent(String clientId, String eventType) {
}
