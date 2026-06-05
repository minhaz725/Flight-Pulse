package com.minhaz.flightpulse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_history")
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "origin", nullable = false, length = 3)
    private String origin;

    @Column(name = "destination", nullable = false, length = 3)
    private String destination;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected PriceHistory() {}

    public PriceHistory(String origin, String destination, BigDecimal price, String currency, Instant observedAt) {
        this.origin = origin;
        this.destination = destination;
        this.price = price;
        this.currency = currency;
        this.observedAt = observedAt;
    }

    public UUID getId() { return id; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public Instant getObservedAt() { return observedAt; }
}
