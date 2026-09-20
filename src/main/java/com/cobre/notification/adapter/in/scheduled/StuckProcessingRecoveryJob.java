package com.cobre.notification.adapter.in.scheduled;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.cobre.notification.adapter.in.scheduled.config.StuckProcessingProperties;
import com.cobre.notification.domain.model.RecoveredStuckEvent;
import com.cobre.notification.domain.port.out.MetricsPort;
import com.cobre.notification.domain.port.out.MetricsTags;
import com.cobre.notification.domain.port.out.NotificationRecordPort;
import com.cobre.notification.domain.port.out.SubscriptionPort;

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
	private final SubscriptionPort subscriptionPort;
	private final MetricsPort metricsPort;
	private final Duration threshold;
	private final Clock clock;

	public StuckProcessingRecoveryJob(NotificationRecordPort notificationRecordPort,
			SubscriptionPort subscriptionPort, MetricsPort metricsPort, StuckProcessingProperties properties) {
		this(notificationRecordPort, subscriptionPort, metricsPort, properties, Clock.systemUTC());
	}

	/** Visible for tests: allows controlling time to exercise the threshold. */
	StuckProcessingRecoveryJob(NotificationRecordPort notificationRecordPort, SubscriptionPort subscriptionPort,
			MetricsPort metricsPort, StuckProcessingProperties properties, Clock clock) {
		this.notificationRecordPort = notificationRecordPort;
		this.subscriptionPort = subscriptionPort;
		this.metricsPort = metricsPort;
		this.threshold = properties.threshold();
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${notification.events.stuck-processing.sweep-interval}")
	public void recoverStuckEvents() {
		Instant olderThan = clock.instant().minus(threshold);
		List<RecoveredStuckEvent> recovered = notificationRecordPort.recoverStuckProcessing(olderThan);
		if (!recovered.isEmpty()) {
			log.warn("Recovered {} notification event(s) stuck in PROCESSING (the process that claimed them likely "
					+ "died mid-delivery) — flipped to FAILED, now replayable again", recovered.size());
			for (RecoveredStuckEvent event : recovered) {
				String webhookUrl = subscriptionPort.findWebHookUrl(event.clientId(), event.eventType())
						.orElse("unknown");
				metricsPort.increment("notification.events.stuck_processing_recovered",
						MetricsTags.EVENT_TYPE.of(event.eventType()), MetricsTags.CLIENT_ID.of(event.clientId()),
						MetricsTags.WEBHOOK.of(webhookUrl));
			}
		}
	}
}
