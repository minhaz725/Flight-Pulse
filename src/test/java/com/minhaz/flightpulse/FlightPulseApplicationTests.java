package com.minhaz.flightpulse;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// boots the full context against a real postgres so flyway migrations run
@SpringBootTest
@Testcontainers
class FlightPulseApplicationTests {

	@Container
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	// some docker providers (e.g. rancher desktop) forward published ports to localhost with a
	// short delay, so wait for the mapped port to actually accept connections before wiring it in
	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		awaitPortOpen(postgres.getHost(), postgres.getMappedPort(5432), Duration.ofSeconds(60));
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	private static void awaitPortOpen(String host, int port, Duration timeout) {
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(host, port), 1000);
				return;
			}
			catch (Exception ignored) {
				sleep(500);
			}
		}
		throw new IllegalStateException("port " + host + ":" + port + " did not open within " + timeout);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	@Test
	void contextLoads() {
	}

}
