package com.minhaz.flightpulse.repository;

import com.minhaz.flightpulse.model.SubscriptionStatus;
import com.minhaz.flightpulse.model.UserSubscription;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {

    List<UserSubscription> findByUserId(String userId);

    List<UserSubscription> findByStatus(SubscriptionStatus status);
}
