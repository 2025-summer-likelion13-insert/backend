package com.example.insert.controller;

import com.example.insert.dto.ApiResponse;
import com.example.insert.dto.CreateReviewRequest;
import com.example.insert.dto.ReviewResponse;
import com.example.insert.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${cors.allowed-origins}")
@Tag(name = "리뷰 관리 API", description = "사용자의 장소별 리뷰 작성/조회/수정/삭제 관련 API")
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 리뷰 작성
     */
    @PostMapping
    @Operation(
            summary = "리뷰 작성",
            description = "사용자가 방문한 장소에 대한 리뷰를 작성합니다."
    )
    @Transactional
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @Valid @RequestBody CreateReviewRequest request) {
        
        log.info("리뷰 작성 요청: 사용자={}, 장소={}, 일정={}", 
                request.getUserId(), request.getPlaceId(), request.getScheduleId());
        
        try {
            ReviewResponse review = reviewService.createReview(request);
            return ResponseEntity.ok(ApiResponse.success(review, "리뷰가 성공적으로 작성되었습니다."));
            
        } catch (IllegalArgumentException e) {
            log.warn("리뷰 작성 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
                    
        } catch (Exception e) {
            log.error("리뷰 작성 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("리뷰 작성 중 오류가 발생했습니다."));
        }
    }

    /**
     * 리뷰 수정
     */
    @PutMapping("/{reviewId}")
    @Operation(
            summary = "리뷰 수정",
            description = "기존에 작성한 리뷰를 수정합니다."
    )
    @Transactional
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody CreateReviewRequest request) {
        
        log.info("리뷰 수정 요청: ID={}", reviewId);
        
        try {
            ReviewResponse review = reviewService.updateReview(reviewId, request);
            return ResponseEntity.ok(ApiResponse.success(review, "리뷰가 성공적으로 수정되었습니다."));
            
        } catch (IllegalArgumentException e) {
            log.warn("리뷰 수정 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
                    
        } catch (Exception e) {
            log.error("리뷰 수정 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("리뷰 수정 중 오류가 발생했습니다."));
        }
    }

    /**
     * 리뷰 조회 (ID로)
     */
    @GetMapping("/{reviewId}")
    @Operation(
            summary = "리뷰 조회",
            description = "특정 리뷰의 상세 정보를 조회합니다."
    )
    public ResponseEntity<ApiResponse<ReviewResponse>> getReviewById(
            @Parameter(description = "리뷰 ID", required = true, example = "1")
            @PathVariable Long reviewId) {
        log.info("리뷰 조회 요청: ID={}", reviewId);
        
        try {
            ReviewResponse review = reviewService.getReviewById(reviewId);
            return ResponseEntity.ok(ApiResponse.success(review, "리뷰를 성공적으로 조회했습니다."));
            
        } catch (IllegalArgumentException e) {
            log.warn("리뷰 조회 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("리뷰 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("리뷰 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 사용자의 특정 일정에 대한 모든 리뷰 조회
     */
    @GetMapping("/users/{userId}/schedules/{scheduleId}")
    @Operation(
            summary = "사용자 일정별 리뷰 조회",
            description = "특정 사용자의 특정 일정에 대한 모든 리뷰를 조회합니다."
    )
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviewsByUserAndSchedule(
            @PathVariable Long userId,
            @PathVariable Long scheduleId) {
        
        log.info("사용자 일정 리뷰 조회: 사용자={}, 일정={}", userId, scheduleId);
        
        try {
            List<ReviewResponse> reviews = reviewService.getReviewsByUserAndSchedule(userId, scheduleId);
            return ResponseEntity.ok(ApiResponse.success(reviews, "리뷰를 성공적으로 조회했습니다."));
            
        } catch (Exception e) {
            log.error("리뷰 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("리뷰 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 특정 장소의 모든 리뷰 조회
     */
    @GetMapping("/places/{placeId}")
    @Operation(
            summary = "장소별 리뷰 조회",
            description = "특정 장소에 대한 모든 사용자의 리뷰를 조회합니다."
    )
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviewsByPlace(
            @Parameter(description = "장소 ID", required = true, example = "1")
            @PathVariable Long placeId) {
        log.info("장소 리뷰 조회: 장소={}", placeId);
        
        try {
            List<ReviewResponse> reviews = reviewService.getReviewsByPlace(placeId);
            return ResponseEntity.ok(ApiResponse.success(reviews, "장소 리뷰를 성공적으로 조회했습니다."));
            
        } catch (Exception e) {
            log.error("장소 리뷰 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("장소 리뷰 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 리뷰 작성 가능한 장소 목록 조회
     */
    @GetMapping("/users/{userId}/schedules/{eventId}/reviewable")
    @Operation(
            summary = "리뷰 작성 가능한 장소 조회",
            description = "일정에 추가되었지만 아직 리뷰를 작성하지 않은 장소들을 조회합니다."
    )
    public ResponseEntity<ApiResponse<List<ReviewResponse.PlaceInfo>>> getReviewablePlaces(
            @PathVariable Long userId,
            @PathVariable Long scheduleId) {
        
        log.info("리뷰 작성 가능한 장소 조회: 사용자={}, 일정={}", userId, scheduleId);
        
        try {
            List<ReviewResponse.PlaceInfo> places = reviewService.getReviewablePlaces(userId, scheduleId);
            return ResponseEntity.ok(ApiResponse.success(places, "리뷰 작성 가능한 장소를 성공적으로 조회했습니다."));
            
        } catch (Exception e) {
            log.error("리뷰 작성 가능한 장소 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("리뷰 작성 가능한 장소 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 리뷰 삭제
     */
    @DeleteMapping("/{reviewId}")
    @Operation(
            summary = "리뷰 삭제",
            description = "작성한 리뷰를 삭제합니다."
    )
    @Transactional
    public ResponseEntity<ApiResponse<String>> deleteReview(
            @PathVariable Long reviewId,
            @RequestParam Long userId) {
        
        log.info("리뷰 삭제 요청: ID={}, 사용자={}", reviewId, userId);
        
        try {
            reviewService.deleteReview(reviewId, userId);
            return ResponseEntity.ok(ApiResponse.success("리뷰가 성공적으로 삭제되었습니다.", "리뷰가 성공적으로 삭제되었습니다."));
            
        } catch (IllegalArgumentException e) {
            log.warn("리뷰 삭제 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
                    
        } catch (Exception e) {
            log.error("리뷰 삭제 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("리뷰 삭제 중 오류가 발생했습니다."));
        }
    }
}
