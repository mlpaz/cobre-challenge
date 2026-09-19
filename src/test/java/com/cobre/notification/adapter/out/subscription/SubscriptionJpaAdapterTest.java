package com.cobre.notification.adapter.out.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.model.WebhookCircuitState;

/**
 * Full context against H2 (PostgreSQL compatibility mode), same approach as
 * NotificationRecordJpaAdapterTest: Boot 4 dropped @DataJpaTest.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:subscriptions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password="
})
@Transactional
class SubscriptionJpaAdapterTest {

	@Autowired
	private SubscriptionJpaAdapter adapter;

	@Autowired
	private SubscriptionJpaRepository repository;

	@Test
	void returnsEmptyWhenThereIsNoSubscription() {
		assertThat(adapter.findWebHookUrl("CLIENT001", "credit_card_payment")).isEmpty();
	}

	@Test
	void returnsTheWebHookUrlOnceSubscribed() {
		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/a"));

		assertThat(adapter.findWebHookUrl("CLIENT001", "credit_card_payment"))
				.contains("https://client.example.com/hooks/a");
	}

	@Test
	void subscribingAgainForTheSamePairUpdatesTheUrlInsteadOfAddingASecondRow() {
		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/old"));
		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/new"));

		assertThat(repository.count()).isEqualTo(1);
		assertThat(adapter.findWebHookUrl("CLIENT001", "credit_card_payment"))
				.contains("https://client.example.com/hooks/new");
	}

	@Test
	void doesNotConfuseDifferentEventTypesForTheSameUser() {
		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/payments"));

		assertThat(adapter.findWebHookUrl("CLIENT001", "debit_card_withdrawal")).isEmpty();
	}

	@Test
	void newSubscriptionsStartWithAHealthyCircuitBreakerState() {
		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/a"));

		SubscriptionEntity entity = entity();
		assertThat(entity.getSuccessScore()).isEqualTo(100);
		assertThat(entity.getTotalCalls()).isZero();
		assertThat(entity.getCircuitState()).isEqualTo(WebhookCircuitState.CLOSED);
	}

	@Test
	void changingTheWebHookUrlResetsTheCircuitBreakerState() {
		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/old"));
		degradeCircuitBreakerState();

		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/new"));

		SubscriptionEntity entity = entity();
		assertThat(entity.getSuccessScore()).isEqualTo(100);
		assertThat(entity.getTotalCalls()).isZero();
		assertThat(entity.getCircuitState()).isEqualTo(WebhookCircuitState.CLOSED);
	}

	@Test
	void reSubscribingWithTheSameUrlDoesNotResetTheCircuitBreakerState() {
		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/a"));
		degradeCircuitBreakerState();

		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/a"));

		SubscriptionEntity entity = entity();
		assertThat(entity.getSuccessScore()).isEqualTo(10);
		assertThat(entity.getTotalCalls()).isEqualTo(5);
		assertThat(entity.getCircuitState()).isEqualTo(WebhookCircuitState.OPEN);
	}

	private void degradeCircuitBreakerState() {
		entity().recordCircuitState(10, 5, WebhookCircuitState.OPEN, Instant.parse("2024-01-01T00:00:00Z"), 0);
	}

	private SubscriptionEntity entity() {
		return repository.findByUserIdAndEventType("CLIENT001", "credit_card_payment").orElseThrow();
	}
}
