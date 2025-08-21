package com.example.insert.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PointLedgerItem {
    private final String type;           // REVIEW | FAVORITE (임시)
    private final int delta;             // +1 / -1 (회수 정책 쓰면)
    private final Long sourceId;         // reviewId or placeId
    private final String reason;         // "리뷰 작성 적립" 등
    private final LocalDateTime createdAt;
}

