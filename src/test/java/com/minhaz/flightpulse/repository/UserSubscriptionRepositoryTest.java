package com.minhaz.flightpulse.repository;

import com.minhaz.flightpulse.model.AlertType;
import com.minhaz.flightpulse.model.PreferredChannel;
import com.minhaz.flightpulse.model.SubscriptionStatus;
import com.minhaz.flightpulse.model.UserSubscription;
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
class UserSubscriptionRepositoryTest {

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
    private UserSubscriptionRepository subscriptionRepository;

    private static final LocalDate FUTURE_FROM = LocalDate.now().plusDays(30);
    private static final LocalDate FUTURE_TO = LocalDate.now().plusDays(60);

    @Test
    void savesSubscriptionWithDefaultChannel() {
        UserSubscription sub = new UserSubscription("user-1", "LHR", "JFK",
                FUTURE_FROM, FUTURE_TO, AlertType.THRESHOLD, BigDecimal.valueOf(500), null, null);

        UserSubscription saved = subscriptionRepository.save(sub);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPreferredChannel()).isEqualTo(PreferredChannel.LOG);
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getBestPriceSeen()).isNull();
    }

    @Test
    void savesSubscriptionWithTelegramChannel() {
        UserSubscription sub = new UserSubscription("user-2", null, "DXB",
                FUTURE_FROM, FUTURE_TO, AlertType.NEW_LOW, null, null, PreferredChannel.TELEGRAM);

        UserSubscription saved = subscriptionRepository.save(sub);

        assertThat(saved.getPreferredChannel()).isEqualTo(PreferredChannel.TELEGRAM);
        assertThat(saved.getAlertType()).isEqualTo(AlertType.NEW_LOW);
    }

    @Test
    void findsByUserId() {
        subscriptionRepository.save(new UserSubscription("user-find-test", "AMS", "BKK",
                FUTURE_FROM, FUTURE_TO, AlertType.THRESHOLD, BigDecimal.valueOf(400), null, PreferredChannel.EMAIL));
        subscriptionRepository.save(new UserSubscription("user-find-test", "LHR", null,
                FUTURE_FROM, FUTURE_TO, AlertType.SCORE, null, 70, PreferredChannel.LOG));

        List<UserSubscription> subs = subscriptionRepository.findByUserId("user-find-test");

        assertThat(subs).hasSize(2);
    }

    @Test
    void findsOnlyActiveSubscriptions() {
        UserSubscription active = subscriptionRepository.save(new UserSubscription(
                "user-active-test", "SIN", "SYD", FUTURE_FROM, FUTURE_TO,
                AlertType.NEW_LOW, null, null, PreferredChannel.LOG));
        UserSubscription expired = subscriptionRepository.save(new UserSubscription(
                "user-active-test", "CDG", "JFK", FUTURE_FROM, FUTURE_TO,
                AlertType.THRESHOLD, BigDecimal.valueOf(600), null, PreferredChannel.LOG));
        expired.expire();
        subscriptionRepository.save(expired);

        List<UserSubscription> activeSubs = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE);

        assertThat(activeSubs).anyMatch(s -> s.getId().equals(active.getId()));
        assertThat(activeSubs).noneMatch(s -> s.getId().equals(expired.getId()));
    }

    @Test
    void updatesBestPriceSeen() {
        UserSubscription sub = subscriptionRepository.save(new UserSubscription(
                "user-newlow-test", "LHR", "JFK", FUTURE_FROM, FUTURE_TO,
                AlertType.NEW_LOW, null, null, PreferredChannel.LOG));

        sub.updateBestPrice(BigDecimal.valueOf(850));
        UserSubscription updated = subscriptionRepository.save(sub);

        assertThat(updated.getBestPriceSeen()).isEqualByComparingTo("850");
    }
}
