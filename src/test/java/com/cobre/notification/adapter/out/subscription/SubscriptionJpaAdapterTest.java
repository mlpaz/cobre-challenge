package com.cobre.notification.adapter.out.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.cobre.notification.AbstractPostgresIntegrationTest;
import com.cobre.notification.adapter.out.circuitbreaker.WebhookCircuitBreakerJpaRepository;
import com.cobre.notification.domain.model.Subscription;

/**
 * Full context against a real PostgreSQL instance (see
 * {@link AbstractPostgresIntegrationTest}), same approach as
 * NotificationRecordJpaAdapterTest: Boot 4 dropped @DataJpaTest.
 *
 * <p>Behavior that spans into the webhook circuit breaker's own state (new
 * subscriptions starting healthy, a URL change resetting it, {@code
 * findByUserId} reporting score/open) is covered by
 * {@code WebhookCircuitBreakerJpaAdapterTest} instead, since asserting on it
 * needs package-private access to {@code WebhookCircuitBreakerEntity}.
 */
@SpringBootTest
@Transactional
class SubscriptionJpaAdapterTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private SubscriptionJpaAdapter adapter;

	@Autowired
	private SubscriptionJpaRepository repository;

	@Autowired
	private WebhookCircuitBreakerJpaRepository circuitBreakerRepository;

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
	void deletesAnExistingSubscription() {
		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/a"));

		adapter.delete("CLIENT001", "credit_card_payment");

		assertThat(adapter.findWebHookUrl("CLIENT001", "credit_card_payment")).isEmpty();
	}

	@Test
	void deletingTheOnlySubscriptionUsingAWebhookRemovesItsCircuitBreakerRow() {
		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/a"));

		adapter.delete("CLIENT001", "credit_card_payment");

		assertThat(circuitBreakerRepository.findByClientIdAndWebHookUrl("CLIENT001",
				"https://client.example.com/hooks/a")).isEmpty();
	}

	@Test
	void deletingASubscriptionThatSharesAWebhookWithAnotherEventTypeKeepsTheCircuitBreakerRow() {
		// Both event types point at the exact same webhook -- deleting one
		// should not wipe out the shared breaker the other still relies on.
		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/a"));
		adapter.save(new Subscription("CLIENT001", "debit_card_withdrawal", "https://client.example.com/hooks/a"));

		adapter.delete("CLIENT001", "credit_card_payment");

		assertThat(circuitBreakerRepository.findByClientIdAndWebHookUrl("CLIENT001",
				"https://client.example.com/hooks/a")).isPresent();
	}

	@Test
	void deletingOneEventTypeDoesNotAffectAnotherSubscriptionOfTheSameUser() {
		adapter.save(new Subscription("CLIENT001", "credit_card_payment", "https://client.example.com/hooks/a"));
		adapter.save(new Subscription("CLIENT001", "debit_card_withdrawal", "https://client.example.com/hooks/b"));

		adapter.delete("CLIENT001", "credit_card_payment");

		assertThat(adapter.findWebHookUrl("CLIENT001", "debit_card_withdrawal"))
				.contains("https://client.example.com/hooks/b");
	}
}
