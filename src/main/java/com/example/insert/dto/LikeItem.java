package com.example.insert.dto;

import java.time.LocalDateTime;

public record LikeItem(
        String externalId,
        LocalDateTime likedAt
) {}
