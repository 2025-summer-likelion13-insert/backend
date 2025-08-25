package com.example.insert.dto;

import com.example.insert.dto.LikeItem;

import java.util.List;

public record LikePageResponse(
        List<LikeItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
