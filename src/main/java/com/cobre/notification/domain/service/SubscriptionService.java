package com.cobre.notification.domain.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.cobre.notification.domain.exception.SubscriptionNotFoundException;
import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.port.in.QuerySubscriptionsUseCase;
import com.cobre.notification.domain.port.in.SubscribeUseCase;
import com.cobre.notification.domain.port.in.UpdateSubscriptionUseCase;
import com.cobre.notification.domain.port.out.SubscriptionPort;

@Service
public class SubscriptionService implements SubscribeUseCase, UpdateSubscriptionUseCase, QuerySubscriptionsUseCase {

	private final SubscriptionPort subscriptionPort;

	public SubscriptionService(SubscriptionPort subscriptionPort) {
		this.subscriptionPort = subscriptionPort;
	}

	@Override
	public void subscribe(Subscription subscription) {
		subscriptionPort.save(subscription);
	}

	@Override
	public void update(Subscription subscription) {
		if (subscriptionPort.findWebHookUrl(subscription.userId(), subscription.eventType()).isEmpty()) {
			throw new SubscriptionNotFoundException(subscription.userId(), subscription.eventType());
		}
		subscriptionPort.save(subscription);
	}

	@Override
	public List<Subscription> listByUserId(String userId) {
		return subscriptionPort.findByUserId(userId);
	}
}
