package com.example.insert.dto;

import com.example.insert.entity.Perform;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL) // ← 선택: null인 필드는 JSON에서 숨김
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
                p.getExternalId(),
                p.getTitle(),
                p.getStartDate(),
                p.getEndDate(),
                p.getVenueName(),
                p.getSynopsis(),
                p.getPosterUrl(),
                p.getState(),
                p.getGenre(),
                p.getArea(),
                p.getSigunguCode()
        );
    }
}
