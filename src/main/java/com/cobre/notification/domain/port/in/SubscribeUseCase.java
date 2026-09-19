package com.cobre.notification.domain.port.in;

import com.cobre.notification.domain.model.Subscription;

public interface SubscribeUseCase {

	void subscribe(Subscription subscription);
}
