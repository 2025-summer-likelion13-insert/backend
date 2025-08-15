package com.example.insert.controller;

import com.example.insert.dto.PlaceRecommendationResponse;
import com.example.insert.dto.AddPlaceToScheduleRequest;
import com.example.insert.entity.UserSchedule;
import com.example.insert.dto.ApiResponse;
import com.example.insert.repository.UserScheduleRepository;
import com.example.insert.repository.RecommendedPlaceRepository;
import com.example.insert.repository.ReviewRepository;
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
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${cors.allowed-origins}")
@Tag(name = "사용자 일정 관리 API", description = "사용자의 장소 일정 추가/조회/수정/삭제 관련 API")
public class UserScheduleController {
    
                    private final UserScheduleRepository userScheduleRepository;
                private final RecommendedPlaceRepository recommendedPlaceRepository;
                private final ReviewRepository reviewRepository;
    
    @PostMapping("/places")
    @Operation(
            summary = "일정에 장소 추가",
            description = "사용자의 특정 이벤트 일정에 추천된 장소를 추가합니다."
    )
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> addPlaceToSchedule(
            @Parameter(description = "장소 추가 요청 정보", required = true)
            @Valid @RequestBody AddPlaceToScheduleRequest request) {
        
        Long userId = request.getUserId();
        Long eventId = request.getEventId();
        Long placeId = request.getPlaceId();
        
        log.info("일정에 장소 추가 요청: 사용자={}, 이벤트={}, 장소={}", userId, eventId, placeId);
        
        try {
            // 장소가 실제로 존재하는지 확인
            var place = recommendedPlaceRepository.findById(placeId);
            if (place.isEmpty()) {
                log.warn("장소 ID {}를 찾을 수 없습니다", placeId);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("존재하지 않는 장소입니다."));
            }
            
            // UserSchedule 엔티티 생성 및 저장
            UserSchedule userSchedule = UserSchedule.builder()
                    .userId(userId)
                    .eventId(eventId)
                    .placeId(placeId)
                    .isVisited(false)
                    .build();
            
            UserSchedule savedSchedule = userScheduleRepository.save(userSchedule);
            
            log.info("장소가 일정에 성공적으로 추가됨: {}", savedSchedule.getId());
            
            return ResponseEntity.ok(ApiResponse.success(Map.of("message", "장소가 일정에 추가되었습니다.", "scheduleId", String.valueOf(savedSchedule.getId())), "장소가 일정에 성공적으로 추가되었습니다."));
            
        } catch (Exception e) {
            log.error("일정에 장소 추가 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("일정에 장소를 추가할 수 없습니다."));
        }
    }
    
    @GetMapping("/users/{userId}/events/{eventId}")
    @Operation(
            summary = "사용자 일정 조회",
            description = "특정 사용자의 특정 이벤트에 대한 일정을 조회합니다."
    )
    public ResponseEntity<ApiResponse<List<PlaceRecommendationResponse.PlaceInfo>>> getUserSchedule(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "이벤트 ID", required = true, example = "1")
            @PathVariable Long eventId) {
        
        log.info("사용자 일정 조회: 사용자={}, 이벤트={}", userId, eventId);
        
        try {
            // 실제 데이터베이스에서 사용자 일정 조회
            List<UserSchedule> userSchedules = userScheduleRepository.findByUserIdAndEventId(userId, eventId);
            
                                    // UserSchedule을 PlaceInfo로 변환 (리뷰 작성 여부 포함)
                        List<PlaceRecommendationResponse.PlaceInfo> schedulePlaces = new ArrayList<>();
                        for (UserSchedule schedule : userSchedules) {
                            var place = recommendedPlaceRepository.findById(schedule.getPlaceId());
                            if (place.isPresent()) {
                                PlaceRecommendationResponse.PlaceInfo placeInfo = convertToPlaceInfo(place.get());
                                
                                // 리뷰 작성 여부 확인
                                boolean hasReview = reviewRepository
                                        .findByUserIdAndPlaceIdAndScheduleId(userId, schedule.getPlaceId(), eventId)
                                        .isPresent();
                                placeInfo.setHasReview(hasReview);
                                
                                schedulePlaces.add(placeInfo);
                            }
                        }
            
            log.info("사용자 일정 조회 완료: {}개 장소", schedulePlaces.size());
            
            return ResponseEntity.ok(ApiResponse.success(schedulePlaces, "사용자 일정을 성공적으로 조회했습니다."));
            
        } catch (Exception e) {
            log.error("사용자 일정 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("사용자 일정을 조회할 수 없습니다."));
        }
    }
    

    
    @DeleteMapping("/users/{userId}/events/{eventId}/places/{placeId}")
    @Operation(
            summary = "장소 ID로 일정 제거",
            description = "사용자 ID, 이벤트 ID, 장소 ID를 사용하여 해당 장소의 일정을 삭제합니다."
    )
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> removePlaceByPlaceId(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "이벤트 ID", required = true, example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "장소 ID", required = true, example = "3")
            @PathVariable Long placeId) {
        
        log.info("장소 ID로 일정 제거 요청: 사용자={}, 이벤트={}, 장소={}", userId, eventId, placeId);
        
        try {
            // 해당 사용자, 이벤트, 장소의 일정 찾기
            var schedule = userScheduleRepository.findByUserIdAndEventIdAndPlaceId(userId, eventId, placeId);
            if (schedule.isEmpty()) {
                log.warn("해당 조건의 일정을 찾을 수 없습니다: 사용자={}, 이벤트={}, 장소={}", userId, eventId, placeId);
                return ResponseEntity.notFound().build();
            }
            
            userScheduleRepository.deleteById(schedule.get().getId());
            
            log.info("장소 ID {}의 일정이 성공적으로 제거됨", placeId);
            
            return ResponseEntity.ok(ApiResponse.success(Map.of("message", "장소의 일정이 제거되었습니다."), "장소의 일정이 성공적으로 제거되었습니다."));
            
        } catch (Exception e) {
            log.error("장소 ID로 일정 제거 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("일정을 제거할 수 없습니다."));
        }
    }
    
    @DeleteMapping("/users/{userId}/events/{eventId}")
    @Operation(
            summary = "일정 전체 삭제",
            description = "사용자의 특정 이벤트에 대한 모든 일정을 삭제합니다."
    )
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteAllSchedules(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "이벤트 ID", required = true, example = "1")
            @PathVariable Long eventId) {
        
        log.info("일정 전체 삭제 요청: 사용자={}, 이벤트={}", userId, eventId);
        
        try {
            // 해당 사용자와 이벤트의 모든 일정 조회
            List<UserSchedule> schedules = userScheduleRepository.findByUserIdAndEventId(userId, eventId);
            
            if (schedules.isEmpty()) {
                log.warn("삭제할 일정이 없습니다: 사용자={}, 이벤트={}", userId, eventId);
                return ResponseEntity.ok(ApiResponse.success(Map.of("message", "삭제할 일정이 없습니다."), "삭제할 일정이 없습니다."));
            }
            
            // 모든 일정 삭제 (더 효율적인 방법)
            userScheduleRepository.deleteByUserIdAndEventId(userId, eventId);
            
            log.info("일정 전체 삭제 완료: {}개 일정", schedules.size());
            
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of("message", "일정이 전체 삭제되었습니다.", "deletedCount", String.valueOf(schedules.size())), 
                    "일정이 전체 삭제되었습니다."));
            
        } catch (Exception e) {
            log.error("일정 전체 삭제 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("일정을 삭제할 수 없습니다."));
        }
    }
    
    @PutMapping("/places/{scheduleId}/visit")
    @Operation(
            summary = "장소 방문 상태 업데이트",
            description = "사용자의 일정에서 특정 장소의 방문 상태를 업데이트합니다."
    )
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> markPlaceAsVisited(
            @Parameter(description = "일정 ID", required = true, example = "1")
            @PathVariable Long scheduleId,
            @Parameter(description = "방문 상태 업데이트 요청", required = true)
            @RequestBody Map<String, Boolean> request) {
        
        Boolean isVisited = request.get("isVisited");
        
        log.info("장소 방문 상태 업데이트: 일정ID={}, 방문여부={}", scheduleId, isVisited);
        
        try {
            // 실제 데이터베이스에서 업데이트
            var schedule = userScheduleRepository.findById(scheduleId);
            if (schedule.isEmpty()) {
                log.warn("일정 ID {}를 찾을 수 없습니다", scheduleId);
                return ResponseEntity.notFound().build();
            }
            
            UserSchedule userSchedule = schedule.get();
            userSchedule.setIsVisited(isVisited);
            userScheduleRepository.save(userSchedule);
            
            log.info("장소 방문 상태가 성공적으로 업데이트됨");
            
            return ResponseEntity.ok(ApiResponse.success(Map.of("message", "방문 상태가 업데이트되었습니다."), "방문 상태가 성공적으로 업데이트되었습니다."));
            
        } catch (Exception e) {
            log.error("장소 방문 상태 업데이트 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("방문 상태를 업데이트할 수 없습니다."));
        }
    }
    
    /**
     * RecommendedPlace를 PlaceInfo로 변환
     */
    private PlaceRecommendationResponse.PlaceInfo convertToPlaceInfo(com.example.insert.entity.RecommendedPlace place) {
        return PlaceRecommendationResponse.PlaceInfo.builder()
                .id(place.getId())
                .name(place.getName())
                .description(place.getDescription())
                .imageUrl(place.getImageUrl())
                .address(place.getAddress())
                .rating(place.getRating())
                .priceRange(place.getPriceRange())
                .openingHours(place.getOpeningHours())
                .aiReason(place.getAiReason())
                .distanceFromVenue(place.getDistanceFromVenue())
                .build();
    }
    
}
