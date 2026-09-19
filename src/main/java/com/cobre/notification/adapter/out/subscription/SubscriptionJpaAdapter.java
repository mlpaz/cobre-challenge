package com.cobre.notification.adapter.out.subscription;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.port.out.SubscriptionPort;

@Component
public class SubscriptionJpaAdapter implements SubscriptionPort {

	private final SubscriptionJpaRepository repository;

	public SubscriptionJpaAdapter(SubscriptionJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<String> findWebHookUrl(String userId, String eventType) {
		return repository.findByUserIdAndEventType(userId, eventType).map(SubscriptionEntity::getWebHookUrl);
	}

	@Override
	@Transactional
	public void save(Subscription subscription) {
		repository.findByUserIdAndEventType(subscription.userId(), subscription.eventType())
				.ifPresentOrElse(
						existing -> existing.updateWebHookUrl(subscription.webHookUrl()),
						() -> repository.save(new SubscriptionEntity(subscription, Instant.now())));
	}
}
