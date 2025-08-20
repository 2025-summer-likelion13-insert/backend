package com.example.insert.dto;

import com.example.insert.entity.Perform;

public record PerformCardDto(
        String externalId, String title, String posterUrl
) {
    public static PerformCardDto of(Perform p) {
        return new PerformCardDto(p.getExternalId(), p.getTitle(), p.getPosterUrl());
    }
}
