package com.cobre.notification.domain.port.in;

import java.util.List;

import com.cobre.notification.domain.model.Subscription;

public interface QuerySubscriptionsUseCase {

	List<Subscription> listByUserId(String userId);
}
