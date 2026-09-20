package com.cobre.notification.adapter.out.circuitbreaker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookCircuitBreakerJpaRepository extends JpaRepository<WebhookCircuitBreakerEntity, UUID> {

	Optional<WebhookCircuitBreakerEntity> findByClientIdAndWebHookUrl(String clientId, String webHookUrl);

	List<WebhookCircuitBreakerEntity> findByClientId(String clientId);
}
