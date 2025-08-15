package com.example.insert.controller;

import com.example.insert.dto.*;

import com.example.insert.service.AIRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/place-recommendations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "장소 추천 API", description = "AI 기반 맞춤형 장소 추천 및 상세 정보 조회")
public class PlaceRecommendationController {

    private final AIRecommendationService aiRecommendationService;

    /**
     * AI 기반 맞춤형 장소 추천 생성
     * 사용자의 프로필, 행사장, 이동수단, 커스텀 조건을 고려하여
     * 엑티비티, 식사, 카페 카테고리별로 각각 3개씩 추천
     */
    @PostMapping("/recommendations")
    @Operation(
        summary = "AI 기반 맞춤형 장소 추천 생성",
        description = "사용자의 프로필 타입, 행사장, 이동수단, 커스텀 조건을 분석하여 " +
                    "엑티비티, 식사, 카페 카테고리별로 각각 최소 3개씩 장소를 추천합니다. " +
                    "AI가 사용자 조건에 맞는 최적의 장소를 선별하고, 중복 없이 다양한 옵션을 제공합니다."
    )
    public ResponseEntity<ApiResponse<PlaceRecommendationResponse>> getPlaceRecommendations(
            @RequestBody PlaceRecommendationRequest request,
            @Parameter(description = "사용자 ID (기본값: 1)", example = "1") 
            @RequestParam(defaultValue = "1") Long userId) {
        
        log.info("장소 추천 요청: userId={}, request={}", userId, request);
        
        try {
            // 요청 유효성 검사
            List<String> validationErrors = validateRequest(request);
            if (!validationErrors.isEmpty()) {
                String errorMessage = String.join(", ", validationErrors);
                log.warn("유효성 검사 실패: {}", errorMessage);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.<PlaceRecommendationResponse>builder()
                                .success(false)
                                .message("유효성 검사 실패: " + errorMessage)
                                .build());
            }

            // AI 추천 서비스 호출 (userId 전달)
            PlaceRecommendationResponse response = aiRecommendationService.generateRecommendations(request, userId);
            
            // 각 카테고리별로 최소 3개씩 보장되었는지 확인
            boolean hasMinimumPlaces = response.getRecommendations().stream()
                    .allMatch(cat -> cat.getPlaces().size() >= 3);
            
            if (!hasMinimumPlaces) {
                log.warn("일부 카테고리에 최소 3개 장소가 보장되지 않음");
            }
            
            log.info("장소 추천 성공: userId={}, 추천장소 수={}, 최소 3개 보장={}", userId, 
                    response.getRecommendations().stream()
                            .mapToInt(cat -> cat.getPlaces().size())
                            .sum(),
                    hasMinimumPlaces);
            
            return ResponseEntity.ok(ApiResponse.<PlaceRecommendationResponse>builder()
                    .success(true)
                    .message("장소 추천이 성공적으로 생성되었습니다.")
                    .data(response)
                    .build());

        } catch (Exception e) {
            log.error("장소 추천 생성 중 오류 발생: userId={}", userId, e);
            
            // 에러 메시지가 null이면 기본 메시지 사용
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "알 수 없는 오류가 발생했습니다";
            }
            
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<PlaceRecommendationResponse>builder()
                            .success(false)
                            .message("장소 추천 생성 중 오류가 발생했습니다: " + errorMessage)
                            .build());
        }
    }

    /**
     * 장소 상세 정보 조회
     * 추천된 장소의 상세 정보를 조회합니다
     */
    @GetMapping("/places/{placeId}")
    @Operation(
        summary = "장소 상세 정보 조회",
        description = "추천된 장소의 상세 정보를 조회합니다. " +
                    "장소명, 설명, 주소, 좌표(위도/경도), 평점, 거리, AI 추천 이유 등 " +
                    "지도 표시에 필요한 모든 정보를 제공합니다."
    )
    public ResponseEntity<ApiResponse<PlaceRecommendationResponse.PlaceInfo>> getPlaceDetails(
            @Parameter(description = "장소 ID", example = "67") 
            @PathVariable Long placeId) {
        
        log.info("장소 상세 정보 요청: placeId={}", placeId);
        
        try {
            // AI 추천 서비스에서 장소 상세 정보 조회
            PlaceRecommendationResponse.PlaceInfo placeInfo = aiRecommendationService.getPlaceDetails(placeId);
            
            if (placeInfo == null) {
                log.warn("장소 ID {}를 찾을 수 없습니다", placeId);
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(ApiResponse.<PlaceRecommendationResponse.PlaceInfo>builder()
                    .success(true)
                    .message("장소 상세 정보를 성공적으로 조회했습니다.")
                    .data(placeInfo)
                    .build());
            
        } catch (Exception e) {
            log.error("장소 상세 정보 조회 중 오류 발생: placeId={}", placeId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<PlaceRecommendationResponse.PlaceInfo>builder()
                            .success(false)
                            .message("장소 상세 정보 조회 중 오류가 발생했습니다: " + e.getMessage())
                            .build());
        }
    }

    /**
     * 요청 유효성 검사
     */
    private List<String> validateRequest(PlaceRecommendationRequest request) {
        List<String> errors = new ArrayList<>();
        
        if (request.getVenueName() == null || request.getVenueName().trim().isEmpty()) {
            errors.add("행사장 이름은 필수입니다");
        }
        
        if (request.getProfileType() == null) {
            errors.add("프로필 타입은 필수입니다");
        }
        
        if (request.getTransportationMethod() == null) {
            errors.add("이동 수단은 필수입니다");
        }
        
        if (request.getCustomConditions() == null || request.getCustomConditions().trim().isEmpty()) {
            errors.add("커스텀 조건은 필수입니다");
        }
        
        return errors;
    }
    

}
