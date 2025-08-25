package com.example.insert.service;

import com.example.insert.dto.CreateReviewRequest;
import com.example.insert.dto.ReviewResponse;
import com.example.insert.entity.Review;
import com.example.insert.entity.RecommendedPlace;
import com.example.insert.entity.UserSchedule;
import com.example.insert.repository.ReviewRepository;
import com.example.insert.repository.RecommendedPlaceRepository;
import com.example.insert.repository.UserScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final RecommendedPlaceRepository recommendedPlaceRepository;
    private final UserScheduleRepository userScheduleRepository;



    /**
     * 리뷰 생성
     */
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request) {
        log.info("리뷰 생성 요청: 사용자={}, 장소={}, 일정={}", 
                request.getUserId(), request.getPlaceId(), request.getScheduleId());

        // 일정이 존재하는지 확인
        UserSchedule schedule = userScheduleRepository.findByUserIdAndEventIdAndPlaceId(
                request.getUserId(), request.getScheduleId(), request.getPlaceId())
                .orElseThrow(() -> new IllegalArgumentException("해당 일정을 찾을 수 없습니다."));

        // 이미 리뷰가 작성되었는지 확인
        if (reviewRepository.findByUserIdAndPlaceIdAndScheduleId(
                request.getUserId(), request.getPlaceId(), request.getScheduleId()).isPresent()) {
            throw new IllegalArgumentException("이미 리뷰가 작성되었습니다.");
        }

        // 리뷰 생성
        Review review = Review.builder()
                .userId(request.getUserId())
                .placeId(request.getPlaceId())
                .scheduleId(request.getScheduleId())
                .rating(request.getRating())
                .content(request.getContent())
                .mediaUrls(request.getMediaUrls())
                .isVisited(request.getIsVisited())
                .build();

        Review savedReview = reviewRepository.save(review);
        log.info("리뷰 생성 완료: ID={}", savedReview.getId());



        return convertToReviewResponse(savedReview);
    }

    /**
     * 리뷰 수정
     */
    @Transactional
    public ReviewResponse updateReview(Long reviewId, CreateReviewRequest request) {
        log.info("리뷰 수정 요청: ID={}", reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));

        // 권한 확인 (본인의 리뷰만 수정 가능)
        if (!review.getUserId().equals(request.getUserId())) {
            throw new IllegalArgumentException("본인의 리뷰만 수정할 수 있습니다.");
        }

        // 리뷰 업데이트
        review.setRating(request.getRating());
        review.setContent(request.getContent());
        review.setMediaUrls(request.getMediaUrls());
        review.setIsVisited(request.getIsVisited());

        Review updatedReview = reviewRepository.save(review);
        log.info("리뷰 수정 완료: ID={}", updatedReview.getId());

        return convertToReviewResponse(updatedReview);
    }

    /**
     * 리뷰 조회 (ID로)
     */
    public ReviewResponse getReviewById(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));

        return convertToReviewResponse(review);
    }

    /**
     * 사용자의 특정 일정에 대한 모든 리뷰 조회
     */
    public List<ReviewResponse> getReviewsByUserAndSchedule(Long userId, Long scheduleId) {
        log.info("사용자 일정 리뷰 조회: 사용자={}, 일정={}", userId, scheduleId);

        List<Review> reviews = reviewRepository.findByUserIdAndScheduleId(userId, scheduleId);
        
        return reviews.stream()
                .map(this::convertToReviewResponse)
                .collect(Collectors.toList());
    }

    /**
     * 특정 장소의 모든 리뷰 조회
     */
    public List<ReviewResponse> getReviewsByPlace(Long placeId) {
        log.info("장소 리뷰 조회: 장소={}", placeId);

        List<Review> reviews = reviewRepository.findByPlaceId(placeId);
        
        return reviews.stream()
                .map(this::convertToReviewResponse)
                .collect(Collectors.toList());
    }

    /**
     * 리뷰 삭제
     */
    @Transactional
    public void deleteReview(Long reviewId, Long userId) {
        log.info("리뷰 삭제 요청: ID={}, 사용자={}", reviewId, userId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));

        // 권한 확인 (본인의 리뷰만 삭제 가능)
        if (!review.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 리뷰만 삭제할 수 있습니다.");
        }

        reviewRepository.delete(review);
        log.info("리뷰 삭제 완료: ID={}", reviewId);
    }

    /**
     * 리뷰 작성 가능한 장소 목록 조회 (일정에 추가되었지만 리뷰가 없는 장소)
     */
    public List<ReviewResponse.PlaceInfo> getReviewablePlaces(Long userId, Long scheduleId) {
        log.info("리뷰 작성 가능한 장소 조회: 사용자={}, 일정={}", userId, scheduleId);

        // 일정에 추가된 장소들 조회
        List<UserSchedule> schedules = userScheduleRepository.findByUserIdAndEventId(userId, scheduleId);
        
        return schedules.stream()
                .map(schedule -> {
                    // 이미 리뷰가 작성되었는지 확인
                    boolean hasReview = reviewRepository
                            .findByUserIdAndPlaceIdAndScheduleId(userId, schedule.getPlaceId(), scheduleId)
                            .isPresent();
                    
                    // 리뷰가 없는 장소만 반환
                    if (!hasReview) {
                        RecommendedPlace place = recommendedPlaceRepository.findById(schedule.getPlaceId()).orElse(null);
                        if (place != null) {
                            return ReviewResponse.PlaceInfo.builder()
                                    .placeId(place.getId())
                                    .placeName(place.getName())
                                    .placeCategory(place.getCategory().toString())
                                    .placeAddress(place.getAddress())
                                    .scheduleId(scheduleId)
                                    .build();
                        }
                    }
                    return null;
                })
                .filter(placeInfo -> placeInfo != null)
                .collect(Collectors.toList());
    }

    /**
     * Review를 ReviewResponse로 변환
     */
    private ReviewResponse convertToReviewResponse(Review review) {
        // 장소 정보 조회
        RecommendedPlace place = recommendedPlaceRepository.findById(review.getPlaceId()).orElse(null);
        
        return ReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .placeId(review.getPlaceId())
                .scheduleId(review.getScheduleId())
                .rating(review.getRating())
                .content(review.getContent())
                .mediaUrls(review.getMediaUrls())
                .isVisited(review.getIsVisited())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .placeName(place != null ? place.getName() : "알 수 없음")
                .placeCategory(place != null ? place.getCategory().toString() : "알 수 없음")
                .placeAddress(place != null ? place.getAddress() : "알 수 없음")
                .build();
    }
}
