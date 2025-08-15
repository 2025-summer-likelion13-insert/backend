package com.example.insert.repository;

import com.example.insert.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * 특정 사용자의 특정 장소에 대한 리뷰 조회
     */
    Optional<Review> findByUserIdAndPlaceIdAndScheduleId(Long userId, Long placeId, Long scheduleId);

    /**
     * 특정 사용자의 모든 리뷰 조회
     */
    List<Review> findByUserId(Long userId);

    /**
     * 특정 장소의 모든 리뷰 조회
     */
    List<Review> findByPlaceId(Long placeId);

    /**
     * 특정 일정의 모든 리뷰 조회
     */
    List<Review> findByScheduleId(Long scheduleId);

    /**
     * 특정 사용자의 특정 일정에 대한 모든 리뷰 조회
     */
    List<Review> findByUserIdAndScheduleId(Long userId, Long scheduleId);

    /**
     * 특정 사용자의 방문 완료된 리뷰 조회
     */
    List<Review> findByUserIdAndIsVisitedTrue(Long userId);
}
