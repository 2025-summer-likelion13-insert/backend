package com.example.insert.dto;

public record LikeResponse(
        String externalId,
        boolean liked,
        long likeCount
) {}
