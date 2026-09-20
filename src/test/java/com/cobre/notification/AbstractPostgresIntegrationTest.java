package com.cobre.notification;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every {@code @SpringBootTest} that needs a real PostgreSQL
 * instance instead of H2 -- see the "Tests" section of the README for why
 * H2 isn't a faithful enough substitute (it doesn't replicate PostgreSQL's
 * server-side prepared-statement parameter type inference, which let a real
 * bug through undetected).
 *
 * <p>The container is started once, in a static initializer, and shared by
 * every subclass for the whole test run -- not one container per test
 * class. A {@code static} field belongs to the class that declares it, and
 * Java only runs a class's static initializer once per JVM, so every
 * subclass extending this one sees the exact same already-running
 * container instead of paying its ~10s startup cost again. Testcontainers'
 * Ryuk reaper stops it automatically when the JVM exits; nothing to shut
 * down manually here.
 */
public abstract class AbstractPostgresIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

	static {
		POSTGRES.start();
	}
}
