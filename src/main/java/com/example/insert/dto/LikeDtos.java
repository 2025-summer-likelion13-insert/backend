package com.example.insert.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public class LikeDtos {

    // 토글 요청
    public record ToggleLikeRequest(String externalId) {}

    // 토글 응답
    public record ToggleLikeResponse(boolean liked, Long likeId, Long count) {}

    // 내 찜 목록 항목
    public record LikedPerformItem(
            String externalId, String title, String posterUrl, OffsetDateTime likedAt
    ) {}

    // 목록 응답
    public record MyLikesResponse(List<LikedPerformItem> items, int nextOffset, long total) {}

    // 다건 여부 요청/응답
    public record HasManyRequest(List<String> externalIds) {}
    public record HasManyResponse(Map<String, Boolean> results) {}
}
