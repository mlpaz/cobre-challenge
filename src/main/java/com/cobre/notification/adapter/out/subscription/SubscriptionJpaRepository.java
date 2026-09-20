package com.cobre.notification.adapter.out.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, UUID> {

	Optional<SubscriptionEntity> findByUserIdAndEventType(String userId, String eventType);

	List<SubscriptionEntity> findByUserIdOrderByEventTypeAsc(String userId);

	/** Used to tell whether a (client, webhook URL) pair is still referenced by any subscription. */
	boolean existsByUserIdAndWebHookUrl(String userId, String webHookUrl);
}
