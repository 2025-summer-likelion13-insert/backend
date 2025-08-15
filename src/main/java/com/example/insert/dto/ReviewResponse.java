package com.example.insert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private Long id;
    private Long userId;
    private Long placeId;
    private Long scheduleId;
    private Integer rating;
    private String content;
    private List<String> mediaUrls;
    private Boolean isVisited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 장소 정보 (리뷰와 함께 표시)
    private String placeName;
    private String placeCategory;
    private String placeAddress;
    
    // 내부 클래스: 리뷰 작성 가능한 장소 정보
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceInfo {
        private Long placeId;
        private String placeName;
        private String placeCategory;
        private String placeAddress;
        private Long scheduleId;
    }
}
