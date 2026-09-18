package com.cobre.notification.adapter.out.idempotency.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Proves the idempotency.ttl property actually reaches IdempotencyProperties
 * via Spring's relaxed @ConfigurationProperties binding (there is no
 * @Value("${idempotency.ttl}") anywhere — the prefix + record component name
 * is what wires it).
 */
class IdempotencyPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfig.class);

	@Test
	void bindsTheTtlFromTheIdempotencyTtlProperty() {
		contextRunner.withPropertyValues("idempotency.ttl=45s")
				.run(context -> {
					IdempotencyProperties properties = context.getBean(IdempotencyProperties.class);
					assertThat(properties.ttl()).isEqualTo(Duration.ofSeconds(45));
				});
	}

	@Test
	void theMSuffixMeansMinutesNotMilliseconds() {
		contextRunner.withPropertyValues("idempotency.ttl=1m")
				.run(context -> {
					IdempotencyProperties properties = context.getBean(IdempotencyProperties.class);
					assertThat(properties.ttl()).isEqualTo(Duration.ofMinutes(1));
				});
	}

	@EnableConfigurationProperties(IdempotencyProperties.class)
	@Configuration
	static class TestConfig {
	}
}
