package com.cobre.notification.adapter.out.subscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, UUID> {

	Optional<SubscriptionEntity> findByUserIdAndEventType(String userId, String eventType);

	List<SubscriptionEntity> findByUserIdOrderByEventTypeAsc(String userId);
}
