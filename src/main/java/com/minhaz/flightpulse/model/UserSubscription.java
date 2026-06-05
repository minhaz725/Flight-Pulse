package com.minhaz.flightpulse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_subscription")
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    // null means match any origin
    @Column(name = "origin", length = 3)
    private String origin;

    // null means match any destination
    @Column(name = "destination", length = 3)
    private String destination;

    @Column(name = "max_price", precision = 10, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "min_score")
    private Integer minScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_channel", nullable = false, length = 20)
    private PreferredChannel preferredChannel;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    protected UserSubscription() {}

    public UserSubscription(String userId, String origin, String destination,
                            BigDecimal maxPrice, Integer minScore, PreferredChannel preferredChannel) {
        this.userId = userId;
        this.origin = origin;
        this.destination = destination;
        this.maxPrice = maxPrice;
        this.minScore = minScore;
        this.preferredChannel = preferredChannel != null ? preferredChannel : PreferredChannel.LOG;
        this.active = true;
    }

    public UUID getId() { return id; }
    public String getUserId() { return userId; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public Integer getMinScore() { return minScore; }
    public PreferredChannel getPreferredChannel() { return preferredChannel; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    public void deactivate() { this.active = false; }
}
