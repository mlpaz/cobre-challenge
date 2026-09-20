package com.cobre.notification.domain.port.in;

import java.util.List;

import com.cobre.notification.domain.model.SubscriptionStatus;

public interface QuerySubscriptionsUseCase {

	List<SubscriptionStatus> listByUserId(String userId);
}
