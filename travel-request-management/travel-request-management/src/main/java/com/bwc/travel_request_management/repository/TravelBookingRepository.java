package com.bwc.travel_request_management.repository;

import com.bwc.travel_request_management.entity.TravelBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface TravelBookingRepository extends JpaRepository<TravelBooking, UUID> {
    List<TravelBooking> findByTravelRequest_TravelRequestId(UUID requestId);
}
