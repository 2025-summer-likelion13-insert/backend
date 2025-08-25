package com.example.insert.dto;

import com.example.insert.entity.Perform;
import java.util.Objects;

public record PerformCardDto(
        String externalId,
        String title,
        String posterUrl
) {
    public static PerformCardDto of(Perform p) {
        return new PerformCardDto(
                Objects.toString(p.getExternalId(), ""),
                Objects.toString(p.getTitle(), ""),
                Objects.toString(p.getPosterUrl(), "")
        );
    }
}
