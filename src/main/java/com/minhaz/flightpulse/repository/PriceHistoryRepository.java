package com.minhaz.flightpulse.repository;

import com.minhaz.flightpulse.model.PriceHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, UUID> {

    List<PriceHistory> findByOriginAndDestinationOrderByObservedAtDesc(String origin, String destination);
}
