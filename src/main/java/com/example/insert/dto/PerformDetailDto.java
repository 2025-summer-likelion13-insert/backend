package com.example.insert.dto;

import com.example.insert.entity.Perform;

import java.time.LocalDate;

public record PerformDetailDto(
        String externalId, String title, LocalDate startDate, LocalDate endDate,
        String venueName, String synopsis, String posterUrl
) {
    public static PerformDetailDto of(Perform p) {
        return new PerformDetailDto(
                p.getExternalId(), p.getTitle(), p.getStartDate(), p.getEndDate(),
                p.getVenueName(), p.getSynopsis(), p.getPosterUrl()
        );
    }
}
