package com.example.insert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateReviewRequest {

    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    @NotNull(message = "장소 ID는 필수입니다")
    private Long placeId;

    @NotNull(message = "일정 ID는 필수입니다")
    private Long scheduleId;

    @NotNull(message = "별점은 필수입니다")
    @Min(value = 1, message = "별점은 최소 1점이어야 합니다")
    @Max(value = 5, message = "별점은 최대 5점까지 가능합니다")
    private Integer rating;

    @NotBlank(message = "리뷰 내용은 필수입니다")
    @Size(max = 500, message = "리뷰 내용은 최대 500자까지 가능합니다")
    private String content;

    @Size(max = 20, message = "미디어는 최대 20개까지 첨부 가능합니다")
    private List<String> mediaUrls;

    @NotNull(message = "방문 완료 여부는 필수입니다")
    private Boolean isVisited;
}
