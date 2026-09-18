package com.cobre.notification.adapter.out.idempotency;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cobre.notification.adapter.out.idempotency.config.IdempotencyProperties;
import com.cobre.notification.domain.port.out.IdempotencyPort;

@Configuration
public class IdempotencyStoreConfig {

	@Bean
	public IdempotencyPort idempotencyPort(IdempotencyProperties properties) {
		return new InMemoryIdempotencyStore(properties);
	}
}
