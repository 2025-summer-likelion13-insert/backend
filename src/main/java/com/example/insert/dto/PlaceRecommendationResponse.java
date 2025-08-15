package com.example.insert.dto;

import com.example.insert.entity.RecommendedPlace.PlaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 기반 장소 추천 응답 데이터")
public class PlaceRecommendationResponse {
    
    @Schema(description = "사용자 맞춤 인사말", example = "테스트사용자님을 위한 오늘의 추천 장소 입니다.")
    private String greeting;
    
    @Schema(description = "추천 서비스 소개 문구", example = "인시트가 알려준 맞춤장소로 하루를 시작해보세요")
    private String subtitle;
    
    @Schema(description = "카테고리별 추천 장소 목록")
    private List<CategoryRecommendation> recommendations;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "카테고리별 추천 장소 그룹")
    public static class CategoryRecommendation {
        @Schema(description = "카테고리 구분", example = "CAFE")
        private PlaceCategory category;
        
        @Schema(description = "카테고리 한글 이름", example = "카페 장소 추천")
        private String categoryName;
        
        @Schema(description = "해당 카테고리의 추천 장소 목록")
        private List<PlaceInfo> places;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "추천 장소 상세 정보")
    public static class PlaceInfo {
        @Schema(description = "장소 고유 ID", example = "67")
        private Long id;
        
        @Schema(description = "장소명", example = "스타벅스 SSG랜더스필드2F점")
        private String name;
        
        @Schema(description = "장소 설명", example = "음식점 > 카페 > 커피전문점 > 스타벅스")
        private String description;
        
        @Schema(description = "장소 이미지 URL")
        private String imageUrl;
        
        @Schema(description = "장소 주소", example = "인천 미추홀구 문학동 482")
        private String address;
        
        @Schema(description = "위도 (지도 표시용)", example = "37.4344")
        private Double latitude;
        
        @Schema(description = "경도 (지도 표시용)", example = "126.6941")
        private Double longitude;
        
        @Schema(description = "장소 평점", example = "4.0")
        private Double rating;
        
        @Schema(description = "가격대", example = "정보 없음")
        private String priceRange;
        
        @Schema(description = "영업시간", example = "상세정보 확인")
        private String openingHours;
        
        @Schema(description = "AI 추천 이유", example = "COUPLE에서 즐길 수 있는 스타벅스입니다. 분위기 있는 데이트 코스")
        private String aiReason;
        
        @Schema(description = "행사장으로부터의 거리 (km)", example = "0.29")
        private Double distanceFromVenue;
        
        @Schema(description = "리뷰 작성 여부")
        private Boolean hasReview;
    }
}
