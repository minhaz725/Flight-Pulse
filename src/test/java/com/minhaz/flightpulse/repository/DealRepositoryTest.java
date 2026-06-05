package com.minhaz.flightpulse.repository;

import com.minhaz.flightpulse.model.Deal;
import com.minhaz.flightpulse.model.DealStatus;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
class DealRepositoryTest {

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
    private DealRepository dealRepository;

    @Test
    void savesAndRetrievesDeal() {
        Deal deal = new Deal("LHR", "JFK", "British Airways", BigDecimal.valueOf(399.99),
                "GBP", LocalDate.now().plusDays(30), null, BigDecimal.valueOf(25.0),
                "mock-json", "ext-001");

        Deal saved = dealRepository.save(deal);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(DealStatus.INGESTED);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findsByStatus() {
        dealRepository.save(new Deal("LHR", "JFK", "BA", BigDecimal.valueOf(300), "GBP",
                LocalDate.now().plusDays(10), null, BigDecimal.valueOf(20), "mock-json", "ext-002"));

        List<Deal> ingested = dealRepository.findByStatus(DealStatus.INGESTED);

        assertThat(ingested).isNotEmpty();
        assertThat(ingested).allMatch(d -> d.getStatus() == DealStatus.INGESTED);
    }

    @Test
    void findsByOriginAndDestination() {
        dealRepository.save(new Deal("AMS", "DXB", "KLM", BigDecimal.valueOf(450), "EUR",
                LocalDate.now().plusDays(15), null, BigDecimal.valueOf(15), "mock-xml", "ext-003"));

        List<Deal> results = dealRepository.findByOriginAndDestination("AMS", "DXB");

        assertThat(results).isNotEmpty();
        assertThat(results.stream().anyMatch(d -> "KLM".equals(d.getAirline()))).isTrue();
    }

    @Test
    void detectsDuplicateByExternalIdAndAdapter() {
        dealRepository.save(new Deal("CDG", "SIN", "Air France", BigDecimal.valueOf(600), "EUR",
                LocalDate.now().plusDays(20), null, BigDecimal.valueOf(30), "mock-xml", "ext-dup-01"));

        assertThat(dealRepository.existsByExternalIdAndSourceAdapter("ext-dup-01", "mock-xml")).isTrue();
        assertThat(dealRepository.existsByExternalIdAndSourceAdapter("ext-dup-01", "mock-json")).isFalse();
    }

    @Test
    void statusTransitionPersists() {
        Deal deal = dealRepository.save(new Deal("SYD", "LAX", "Qantas", BigDecimal.valueOf(800), "AUD",
                LocalDate.now().plusDays(45), LocalDate.now().plusDays(55), BigDecimal.valueOf(40),
                "mock-json", "ext-005"));

        deal.setScore(82);
        deal.setStatus(DealStatus.SCORED);
        Deal updated = dealRepository.save(deal);

        assertThat(updated.getStatus()).isEqualTo(DealStatus.SCORED);
        assertThat(updated.getScore()).isEqualTo(82);
    }
}
