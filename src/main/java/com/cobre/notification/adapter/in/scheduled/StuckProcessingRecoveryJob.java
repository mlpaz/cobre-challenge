package com.cobre.notification.adapter.in.scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.cobre.notification.adapter.in.scheduled.config.StuckProcessingProperties;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.NotificationRecordPort;

/**
 * Closes the one failure window that atomically claiming an event
 * (see {@link NotificationRecordPort#tryClaim}) doesn't fix by itself: if the
 * process dies *after* winning the claim but *before* calling
 * {@link NotificationRecordPort#complete}, that row stays {@code PROCESSING}
 * forever — claimed, so nobody will ever retry it, but never resolved. This
 * sweep periodically flips any such row older than the configured threshold
 * to {@code FAILED}, which puts it back in the normal, well-trodden path:
 * visible via {@code GET /notification_events} and recoverable with the
 * existing {@code POST /notification_events/{id}/replay} — no separate
 * recovery mechanism needed.
 *
 * <p>Not component-scanned: wired as a bean by
 * {@link com.cobre.notification.adapter.in.scheduled.config.StuckProcessingRecoveryJobConfig}
 * instead, since a second (test-only) constructor overload would otherwise
 * make Spring's constructor-autowiring resolution ambiguous.
 */
public class StuckProcessingRecoveryJob {

	private static final Logger log = LoggerFactory.getLogger(StuckProcessingRecoveryJob.class);

	private final NotificationRecordPort notificationRecordPort;
	private final MetricsPort metricsPort;
	private final Duration threshold;
	private final Clock clock;

	public StuckProcessingRecoveryJob(NotificationRecordPort notificationRecordPort, MetricsPort metricsPort,
			StuckProcessingProperties properties) {
		this(notificationRecordPort, metricsPort, properties, Clock.systemUTC());
	}

	/** Visible for tests: allows controlling time to exercise the threshold. */
	StuckProcessingRecoveryJob(NotificationRecordPort notificationRecordPort, MetricsPort metricsPort,
			StuckProcessingProperties properties, Clock clock) {
		this.notificationRecordPort = notificationRecordPort;
		this.metricsPort = metricsPort;
		this.threshold = properties.threshold();
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${notification.events.stuck-processing.sweep-interval}")
	public void recoverStuckEvents() {
		Instant olderThan = clock.instant().minus(threshold);
		int recovered = notificationRecordPort.recoverStuckProcessing(olderThan);
		if (recovered > 0) {
			log.warn("Recovered {} notification event(s) stuck in PROCESSING (the process that claimed them likely "
					+ "died mid-delivery) — flipped to FAILED, now replayable again", recovered);
			metricsPort.increment("notification.events.stuck_processing_recovered");
		}
	}
}
