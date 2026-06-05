package com.minhaz.flightpulse.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "deal")
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "origin", nullable = false, length = 3)
    private String origin;

    @Column(name = "destination", nullable = false, length = 3)
    private String destination;

    @Column(name = "airline")
    private String airline;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @Column(name = "discount_percentage", precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(name = "score")
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DealStatus status;

    @Column(name = "source_adapter", nullable = false, length = 100)
    private String sourceAdapter;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    protected Deal() {}

    public Deal(String origin, String destination, String airline, BigDecimal price, String currency,
                LocalDate departureDate, LocalDate returnDate, BigDecimal discountPercentage,
                String sourceAdapter, String externalId) {
        this.origin = origin;
        this.destination = destination;
        this.airline = airline;
        this.price = price;
        this.currency = currency;
        this.departureDate = departureDate;
        this.returnDate = returnDate;
        this.discountPercentage = discountPercentage;
        this.sourceAdapter = sourceAdapter;
        this.externalId = externalId;
        this.status = DealStatus.INGESTED;
    }

    public UUID getId() { return id; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public String getAirline() { return airline; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public LocalDate getDepartureDate() { return departureDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public Integer getScore() { return score; }
    public DealStatus getStatus() { return status; }
    public String getSourceAdapter() { return sourceAdapter; }
    public String getExternalId() { return externalId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setScore(Integer score) { this.score = score; }
    public void setStatus(DealStatus status) { this.status = status; }
}
