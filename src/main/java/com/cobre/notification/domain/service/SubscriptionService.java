package com.cobre.notification.domain.service;

import org.springframework.stereotype.Service;

import com.cobre.notification.domain.model.Subscription;
import com.cobre.notification.domain.port.in.SubscribeUseCase;
import com.cobre.notification.domain.port.out.SubscriptionPort;

@Service
public class SubscriptionService implements SubscribeUseCase {

	private final SubscriptionPort subscriptionPort;

	public SubscriptionService(SubscriptionPort subscriptionPort) {
		this.subscriptionPort = subscriptionPort;
	}

	@Override
	public void subscribe(Subscription subscription) {
		subscriptionPort.save(subscription);
	}
}
