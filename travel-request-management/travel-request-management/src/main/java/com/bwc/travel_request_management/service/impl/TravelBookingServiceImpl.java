package com.bwc.travel_request_management.service.impl;

import com.bwc.travel_request_management.dto.TravelBookingDTO;
import com.bwc.travel_request_management.entity.TravelBooking;
import com.bwc.travel_request_management.entity.TravelRequest;
import com.bwc.travel_request_management.exception.ResourceNotFoundException;
import com.bwc.travel_request_management.mapper.TravelBookingMapper;
import com.bwc.travel_request_management.repository.TravelBookingRepository;
import com.bwc.travel_request_management.repository.TravelRequestRepository;
import com.bwc.travel_request_management.service.TravelBookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TravelBookingServiceImpl implements TravelBookingService {

    private final TravelBookingRepository bookingRepository;
    private final TravelRequestRepository requestRepository;
    private final TravelBookingMapper mapper;

    @Override
    @Transactional
    public TravelBookingDTO addBooking(UUID requestId, TravelBookingDTO bookingDto) {
        TravelRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Travel Request not found with id: " + requestId));

        TravelBooking booking = mapper.toEntity(bookingDto);
        booking.setTravelRequest(request);
        TravelBooking saved = bookingRepository.save(booking);

        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TravelBookingDTO getBooking(UUID id) {
        return bookingRepository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TravelBookingDTO> getBookingsForRequest(UUID requestId) {
        return bookingRepository.findByTravelRequest_TravelRequestId(requestId)
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteBooking(UUID bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new ResourceNotFoundException("Booking not found with id: " + bookingId);
        }
        bookingRepository.deleteById(bookingId);
    }
}