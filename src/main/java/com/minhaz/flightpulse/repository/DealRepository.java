package com.minhaz.flightpulse.repository;

import com.minhaz.flightpulse.model.Deal;
import com.minhaz.flightpulse.model.DealStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealRepository extends JpaRepository<Deal, UUID> {

    List<Deal> findByStatus(DealStatus status);

    List<Deal> findByOriginAndDestination(String origin, String destination);

    boolean existsByExternalIdAndSourceAdapter(String externalId, String sourceAdapter);
}
