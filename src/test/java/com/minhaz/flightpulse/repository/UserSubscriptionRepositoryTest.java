package com.minhaz.flightpulse.repository;

import com.minhaz.flightpulse.model.PreferredChannel;
import com.minhaz.flightpulse.model.UserSubscription;
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

    @Test
    void savesSubscriptionWithDefaultChannel() {
        UserSubscription sub = new UserSubscription("user-1", "LHR", "JFK",
                BigDecimal.valueOf(500), 70, null);

        UserSubscription saved = subscriptionRepository.save(sub);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPreferredChannel()).isEqualTo(PreferredChannel.LOG);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void savesSubscriptionWithTelegramChannel() {
        UserSubscription sub = new UserSubscription("user-2", null, "DXB",
                BigDecimal.valueOf(350), null, PreferredChannel.TELEGRAM);

        UserSubscription saved = subscriptionRepository.save(sub);

        assertThat(saved.getPreferredChannel()).isEqualTo(PreferredChannel.TELEGRAM);
    }

    @Test
    void findsByUserId() {
        subscriptionRepository.save(new UserSubscription("user-find-test", "AMS", "BKK",
                BigDecimal.valueOf(400), 60, PreferredChannel.EMAIL));
        subscriptionRepository.save(new UserSubscription("user-find-test", "LHR", null,
                null, null, PreferredChannel.LOG));

        List<UserSubscription> subs = subscriptionRepository.findByUserId("user-find-test");

        assertThat(subs).hasSize(2);
    }

    @Test
    void findsOnlyActiveSubscriptions() {
        UserSubscription active = subscriptionRepository.save(new UserSubscription(
                "user-active-test", "SIN", "SYD", null, null, PreferredChannel.LOG));
        UserSubscription inactive = subscriptionRepository.save(new UserSubscription(
                "user-active-test", "CDG", "JFK", null, null, PreferredChannel.LOG));
        inactive.deactivate();
        subscriptionRepository.save(inactive);

        List<UserSubscription> activeSubs = subscriptionRepository.findByActiveTrue();

        assertThat(activeSubs).anyMatch(s -> s.getId().equals(active.getId()));
        assertThat(activeSubs).noneMatch(s -> s.getId().equals(inactive.getId()));
    }
}
