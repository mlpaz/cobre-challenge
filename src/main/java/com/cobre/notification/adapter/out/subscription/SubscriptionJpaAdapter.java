package com.cobre.notification.adapter.out.subscription;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cobre.notification.adapter.out.circuitbreaker.WebhookCircuitBreakerEntity;
import com.cobre.notification.adapter.out.circuitbreaker.WebhookCircuitBreakerJpaRepository;
import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.model.SubscriptionStatus;
import com.cobre.notification.domain.model.WebhookCircuitState;
import com.cobre.notification.domain.port.out.SubscriptionPort;

@Component
public class SubscriptionJpaAdapter implements SubscriptionPort {

	private final SubscriptionJpaRepository repository;
	private final WebhookCircuitBreakerJpaRepository circuitBreakerRepository;

	public SubscriptionJpaAdapter(SubscriptionJpaRepository repository,
			WebhookCircuitBreakerJpaRepository circuitBreakerRepository) {
		this.repository = repository;
		this.circuitBreakerRepository = circuitBreakerRepository;
	}

	@Override
	public Optional<String> findWebHookUrl(String userId, String eventType) {
		return repository.findByUserIdAndEventType(userId, eventType).map(SubscriptionEntity::getWebHookUrl);
	}

	@Override
	@Transactional
	public void save(Subscription subscription) {
		SubscriptionEntity existing = repository
				.findByUserIdAndEventType(subscription.userId(), subscription.eventType()).orElse(null);

		if (existing == null) {
			repository.save(new SubscriptionEntity(subscription, Instant.now()));
			ensureCircuitBreakerExists(subscription.userId(), subscription.webHookUrl());
			return;
		}

		// A different URL is potentially a different physical webhook, so this
		// event type now shares whichever breaker already tracks that URL for
		// this client (creating a fresh, healthy one only if none exists yet)
		// instead of carrying over this subscription's own history. The old
		// URL's breaker is only removed if no other event type still uses it —
		// other event types may still be pointing at it.
		if (!existing.getWebHookUrl().equals(subscription.webHookUrl())) {
			String previousUrl = existing.getWebHookUrl();
			existing.updateWebHookUrl(subscription.webHookUrl());
			ensureCircuitBreakerExists(subscription.userId(), subscription.webHookUrl());
			removeCircuitBreakerIfOrphaned(subscription.userId(), previousUrl);
		}
	}

	@Override
	public List<SubscriptionStatus> findByUserId(String userId) {
		List<SubscriptionEntity> subscriptions = repository.findByUserIdOrderByEventTypeAsc(userId);
		Map<String, WebhookCircuitBreakerEntity> breakersByUrl = circuitBreakerRepository.findByClientId(userId)
				.stream().collect(Collectors.toMap(WebhookCircuitBreakerEntity::getWebHookUrl, Function.identity()));

		return subscriptions.stream()
				.map(entity -> {
					WebhookCircuitBreakerEntity breaker = breakersByUrl.get(entity.getWebHookUrl());
					int successScore = breaker != null ? breaker.getSuccessScore() : 100;
					boolean open = breaker != null && breaker.getCircuitState() == WebhookCircuitState.OPEN;
					return new SubscriptionStatus(entity.getUserId(), entity.getEventType(), entity.getWebHookUrl(),
							successScore, open);
				})
				.toList();
	}

	@Override
	@Transactional
	public void delete(String userId, String eventType) {
		repository.findByUserIdAndEventType(userId, eventType).ifPresent(entity -> {
			String webHookUrl = entity.getWebHookUrl();
			repository.delete(entity);
			removeCircuitBreakerIfOrphaned(userId, webHookUrl);
		});
	}

	/** Reuses the existing breaker for this (client, webhook) pair, or starts a fresh healthy one. */
	private void ensureCircuitBreakerExists(String clientId, String webHookUrl) {
		if (circuitBreakerRepository.findByClientIdAndWebHookUrl(clientId, webHookUrl).isEmpty()) {
			circuitBreakerRepository.save(new WebhookCircuitBreakerEntity(clientId, webHookUrl));
		}
	}

	/** Deletes the breaker only if no event type of this client still points at that webhook. */
	private void removeCircuitBreakerIfOrphaned(String clientId, String webHookUrl) {
		if (!repository.existsByUserIdAndWebHookUrl(clientId, webHookUrl)) {
			circuitBreakerRepository.findByClientIdAndWebHookUrl(clientId, webHookUrl)
					.ifPresent(circuitBreakerRepository::delete);
		}
	}
}
