package com.bwc.travel_request_management.controller;

import com.bwc.travel_request_management.dto.TravelBookingDTO;
import com.bwc.travel_request_management.service.TravelBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/travel-bookings")
@RequiredArgsConstructor
@Validated
@Tag(name = "Travel Bookings", description = "Manage bookings like flights, hotels, trains etc.")
public class TravelBookingController {

    private final TravelBookingService service;

    @Operation(summary = "Add a travel booking")
    @PostMapping("/{requestId}")
    public ResponseEntity<TravelBookingDTO> addBooking(
            @Parameter(description = "Travel request ID") @PathVariable UUID requestId,
            @Valid @RequestBody TravelBookingDTO dto) {
        return ResponseEntity.ok(service.addBooking(requestId, dto));
    }

    @Operation(summary = "Get a booking by ID")
    @GetMapping("/{bookingId}")
    public ResponseEntity<TravelBookingDTO> getBooking(
            @Parameter(description = "Booking ID") @PathVariable UUID bookingId) {
        return ResponseEntity.ok(service.getBooking(bookingId));
    }

    @Operation(summary = "List bookings for a request")
    @GetMapping("/by-request/{requestId}")
    public ResponseEntity<List<TravelBookingDTO>> getByRequest(
            @Parameter(description = "Travel request ID") @PathVariable UUID requestId) {
        return ResponseEntity.ok(service.getBookingsForRequest(requestId));
    }

    @Operation(summary = "Delete a booking")
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Booking ID") @PathVariable UUID bookingId) {
        service.deleteBooking(bookingId);
        return ResponseEntity.noContent().build();
    }
}
