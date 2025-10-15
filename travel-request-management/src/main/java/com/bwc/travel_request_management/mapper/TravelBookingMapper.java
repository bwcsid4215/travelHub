package com.bwc.travel_request_management.mapper;

import com.bwc.travel_request_management.dto.TravelBookingDTO;
import com.bwc.travel_request_management.entity.TravelBooking;
import org.springframework.stereotype.Component;

@Component
public class TravelBookingMapper {

    public TravelBookingDTO toDto(TravelBooking entity) {
        if (entity == null) return null;
        return TravelBookingDTO.builder()
                .bookingId(entity.getBookingId())
                .bookingType(entity.getBookingType())
                .details(entity.getDetails())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public TravelBooking toEntity(TravelBookingDTO dto) {
        if (dto == null) return null;
        return TravelBooking.builder()
                .bookingId(dto.getBookingId())
                .bookingType(dto.getBookingType())
                .details(dto.getDetails())
                .notes(dto.getNotes())
                .build();
    }
}