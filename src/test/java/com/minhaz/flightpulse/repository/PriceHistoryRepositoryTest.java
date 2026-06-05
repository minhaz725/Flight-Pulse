package com.minhaz.flightpulse.repository;

import com.minhaz.flightpulse.model.PriceHistory;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PriceHistoryRepositoryTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

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

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Test
    void savesAndRetrieves() {
        PriceHistory entry = new PriceHistory("LHR", "JFK", BigDecimal.valueOf(450), "GBP", Instant.now());

        PriceHistory saved = priceHistoryRepository.save(entry);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getOrigin()).isEqualTo("LHR");
    }

    @Test
    void returnsEntriesOrderedByObservedAtDesc() {
        Instant older = Instant.now().minusSeconds(3600);
        Instant newer = Instant.now().minusSeconds(60);

        priceHistoryRepository.save(new PriceHistory("AMS", "DXB", BigDecimal.valueOf(500), "EUR", older));
        priceHistoryRepository.save(new PriceHistory("AMS", "DXB", BigDecimal.valueOf(420), "EUR", newer));

        List<PriceHistory> history = priceHistoryRepository
                .findByOriginAndDestinationOrderByObservedAtDesc("AMS", "DXB");

        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
        // most recent observation must come first
        assertThat(history.get(0).getObservedAt()).isAfterOrEqualTo(history.get(1).getObservedAt());
    }

    @Test
    void doesNotReturnEntriesForOtherRoutes() {
        priceHistoryRepository.save(new PriceHistory("CDG", "SIN", BigDecimal.valueOf(600), "EUR", Instant.now()));

        List<PriceHistory> history = priceHistoryRepository
                .findByOriginAndDestinationOrderByObservedAtDesc("CDG", "BKK");

        assertThat(history).isEmpty();
    }
}
