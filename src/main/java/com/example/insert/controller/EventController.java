package com.example.insert.controller;

import com.example.insert.dto.ApiResponse;
import com.example.insert.dto.CreateEventRequest;
import com.example.insert.dto.UpdateEventRequest;
import com.example.insert.dto.EventResponse;
import com.example.insert.service.EventService;
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
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${cors.allowed-origins}")
@Tag(name = "이벤트 관리 API", description = "이벤트 생성/조회/수정/삭제 관련 API")
public class EventController {

    private final EventService eventService;

    /**
     * 이벤트 생성
     */
    @PostMapping
    @Operation(
            summary = "이벤트 생성",
            description = "새로운 이벤트를 생성합니다."
    )
    @Transactional
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Parameter(description = "이벤트 생성 요청 정보", required = true)
            @Valid @RequestBody CreateEventRequest request) {
        
        log.info("이벤트 생성 요청: {}", request.getName());
        
        try {
            EventResponse event = eventService.createEvent(request);
            return ResponseEntity.ok(ApiResponse.success(event, "이벤트가 성공적으로 생성되었습니다."));
            
        } catch (Exception e) {
            log.error("이벤트 생성 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("이벤트를 생성할 수 없습니다: " + e.getMessage()));
        }
    }

    /**
     * 모든 이벤트 조회
     */
    @GetMapping
    @Operation(
            summary = "모든 이벤트 조회",
            description = "시스템에 등록된 모든 이벤트를 조회합니다."
    )
    public ResponseEntity<ApiResponse<List<EventResponse>>> getAllEvents() {
        
        log.info("모든 이벤트 조회 요청");
        
        try {
            List<EventResponse> events = eventService.findAllEvents();
            return ResponseEntity.ok(ApiResponse.success(events, "이벤트를 성공적으로 조회했습니다."));
            
        } catch (Exception e) {
            log.error("이벤트 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("이벤트를 조회할 수 없습니다."));
        }
    }

    /**
     * 특정 이벤트 조회
     */
    @GetMapping("/{eventId}")
    @Operation(
            summary = "특정 이벤트 조회",
            description = "이벤트 ID로 특정 이벤트를 조회합니다."
    )
    public ResponseEntity<ApiResponse<EventResponse>> getEventById(
            @Parameter(description = "이벤트 ID", required = true, example = "1")
            @PathVariable Long eventId) {
        
        log.info("특정 이벤트 조회 요청: eventId={}", eventId);
        
        try {
            EventResponse event = eventService.findById(eventId);
            return ResponseEntity.ok(ApiResponse.success(event, "이벤트를 성공적으로 조회했습니다."));
            
        } catch (IllegalArgumentException e) {
            log.warn("이벤트를 찾을 수 없음: eventId={}", eventId);
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("이벤트 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("이벤트를 조회할 수 없습니다."));
        }
    }

    /**
     * 사용자별 이벤트 조회
     */
    @GetMapping("/users/{userId}")
    @Operation(
            summary = "사용자별 이벤트 조회",
            description = "특정 사용자의 모든 이벤트를 조회합니다."
    )
    public ResponseEntity<ApiResponse<List<EventResponse>>> getEventsByUserId(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId) {
        
        log.info("사용자별 이벤트 조회 요청: userId={}", userId);
        
        try {
            List<EventResponse> events = eventService.findEventsByUserId(userId);
            return ResponseEntity.ok(ApiResponse.success(events, "사용자 이벤트를 성공적으로 조회했습니다."));
            
        } catch (Exception e) {
            log.error("사용자 이벤트 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("사용자 이벤트를 조회할 수 없습니다."));
        }
    }

    /**
     * 카테고리별 이벤트 조회
     */
    @GetMapping("/category/{category}")
    @Operation(
            summary = "카테고리별 이벤트 조회",
            description = "특정 카테고리의 모든 이벤트를 조회합니다."
    )
    public ResponseEntity<ApiResponse<List<EventResponse>>> getEventsByCategory(
            @Parameter(description = "카테고리", required = true, example = "FOOD")
            @PathVariable String category) {
        
        log.info("카테고리별 이벤트 조회 요청: category={}", category);
        
        try {
            List<EventResponse> events = eventService.findEventsByCategory(category);
            return ResponseEntity.ok(ApiResponse.success(events, "카테고리별 이벤트를 성공적으로 조회했습니다."));
            
        } catch (Exception e) {
            log.error("카테고리별 이벤트 조회 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("카테고리별 이벤트를 조회할 수 없습니다."));
        }
    }

    /**
     * 이벤트명으로 검색
     */
    @GetMapping("/search")
    @Operation(
            summary = "이벤트명 검색",
            description = "이벤트명으로 이벤트를 검색합니다."
    )
    public ResponseEntity<ApiResponse<List<EventResponse>>> searchEventsByName(
            @Parameter(description = "검색할 이벤트명", required = true, example = "카페")
            @RequestParam String name) {
        
        log.info("이벤트명 검색 요청: name={}", name);
        
        try {
            List<EventResponse> events = eventService.searchEventsByName(name);
            return ResponseEntity.ok(ApiResponse.success(events, "이벤트 검색을 성공적으로 완료했습니다."));
            
        } catch (Exception e) {
            log.error("이벤트 검색 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("이벤트 검색을 할 수 없습니다."));
        }
    }

    /**
     * 공연 ID로 이벤트 검색
     */
    @GetMapping("/external/{externalId}")
    @Operation(
            summary = "공연 ID로 이벤트 검색",
            description = "특정 공연 ID와 연결된 모든 이벤트를 검색합니다."
    )
    public ResponseEntity<ApiResponse<List<EventResponse>>> getEventsByExternalId(
            @Parameter(description = "공연 ID", required = true, example = "concert_12345")
            @PathVariable String externalId) {
        
        log.info("공연 ID로 이벤트 검색 요청: externalId={}", externalId);
        
        try {
            List<EventResponse> events = eventService.findEventsByExternalId(externalId);
            return ResponseEntity.ok(ApiResponse.success(events, "공연 ID로 이벤트를 성공적으로 검색했습니다."));
            
        } catch (Exception e) {
            log.error("공연 ID로 이벤트 검색 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("공연 ID로 이벤트를 검색할 수 없습니다."));
        }
    }

    /**
     * 사용자별 공연 ID로 이벤트 검색
     */
    @GetMapping("/users/{userId}/external/{externalId}")
    @Operation(
            summary = "사용자별 공연 ID로 이벤트 검색",
            description = "특정 사용자의 특정 공연 ID와 연결된 이벤트를 검색합니다."
    )
    public ResponseEntity<ApiResponse<List<EventResponse>>> getEventsByUserIdAndExternalId(
            @Parameter(description = "사용자 ID", required = true, example = "1")
            @PathVariable Long userId,
            @Parameter(description = "공연 ID", required = true, example = "concert_12345")
            @PathVariable String externalId) {
        
        log.info("사용자별 공연 ID로 이벤트 검색 요청: userId={}, externalId={}", userId, externalId);
        
        try {
            List<EventResponse> events = eventService.findEventsByUserIdAndExternalId(userId, externalId);
            return ResponseEntity.ok(ApiResponse.success(events, "사용자별 공연 ID로 이벤트를 성공적으로 검색했습니다."));
            
        } catch (Exception e) {
            log.error("사용자별 공연 ID로 이벤트 검색 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("사용자별 공연 ID로 이벤트를 검색할 수 없습니다."));
        }
    }

    /**
     * 이벤트 수정
     */
    @PutMapping("/{eventId}")
    @Operation(
            summary = "이벤트 수정",
            description = "기존 이벤트 정보를 수정합니다."
    )
    @Transactional
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @Parameter(description = "이벤트 ID", required = true, example = "1")
            @PathVariable Long eventId,
            @Parameter(description = "이벤트 수정 요청 정보", required = true)
            @Valid @RequestBody UpdateEventRequest request) {
        
        log.info("이벤트 수정 요청: eventId={}", eventId);
        
        try {
            EventResponse updatedEvent = eventService.updateEvent(eventId, request);
            return ResponseEntity.ok(ApiResponse.success(updatedEvent, "이벤트가 성공적으로 수정되었습니다."));
            
        } catch (IllegalArgumentException e) {
            log.warn("수정할 이벤트를 찾을 수 없음: eventId={}", eventId);
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("이벤트 수정 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("이벤트를 수정할 수 없습니다: " + e.getMessage()));
        }
    }

    /**
     * 이벤트 삭제
     */
    @DeleteMapping("/{eventId}")
    @Operation(
            summary = "이벤트 삭제",
            description = "특정 이벤트를 삭제합니다."
    )
    @Transactional
    public ResponseEntity<ApiResponse<String>> deleteEvent(
            @Parameter(description = "이벤트 ID", required = true, example = "1")
            @PathVariable Long eventId) {
        
        log.info("이벤트 삭제 요청: eventId={}", eventId);
        
        try {
            eventService.deleteEvent(eventId);
            return ResponseEntity.ok(ApiResponse.success("이벤트가 성공적으로 삭제되었습니다.", "이벤트가 성공적으로 삭제되었습니다."));
            
        } catch (IllegalArgumentException e) {
            log.warn("삭제할 이벤트를 찾을 수 없음: eventId={}", eventId);
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("이벤트 삭제 중 오류 발생", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("이벤트를 삭제할 수 없습니다: " + e.getMessage()));
        }
    }
}
