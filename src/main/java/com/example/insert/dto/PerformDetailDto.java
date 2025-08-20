package com.example.insert.dto;

import com.example.insert.entity.Perform;

import java.time.LocalDate;
import java.util.Objects;

public record PerformDetailDto(
        String externalId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String venueName,
        String synopsis,
        String posterUrl,
        String state,        // 01=공연예정, 02=공연중, 03=공연완료
        String genre,        // 장르
        String area,         // 시/도명 등 원문 문자열
        String sigunguCode   // 구·군 4자리(signgucodesub)
) {
    public static PerformDetailDto of(Perform p) {
        return new PerformDetailDto(
                Objects.toString(p.getExternalId(), ""),
                Objects.toString(p.getTitle(), ""),
                p.getStartDate(),
                p.getEndDate(),
                Objects.toString(p.getVenueName(), ""),
                Objects.toString(p.getSynopsis(), ""),
                Objects.toString(p.getPosterUrl(), ""),
                Objects.toString(p.getState(), ""),
                Objects.toString(p.getGenre(), ""),
                Objects.toString(p.getArea(), ""),
                Objects.toString(p.getSigunguCode(), "")
        );
    }
}
