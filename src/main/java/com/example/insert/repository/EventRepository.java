package com.example.insert.repository;

import com.example.insert.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    
    /**
     * 특정 사용자의 모든 이벤트 조회
     */
    List<Event> findByUserId(Long userId);
    
    /**
     * 특정 카테고리의 이벤트 조회
     */
    List<Event> findByCategory(String category);
    
    /**
     * 특정 날짜 범위의 이벤트 조회
     */
    List<Event> findByEventDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * 특정 지역의 이벤트 조회 (위도/경도 범위)
     */
    List<Event> findByVenueLatitudeBetweenAndVenueLongitudeBetween(
            Double minLat, Double maxLat, Double minLng, Double maxLng);
    
    /**
     * 이벤트명으로 검색
     */
    List<Event> findByNameContainingIgnoreCase(String name);
    
    /**
     * 장소명으로 검색
     */
    List<Event> findByVenueNameContainingIgnoreCase(String venueName);
    
    /**
     * 공연 ID로 이벤트 검색
     */
    List<Event> findByExternalId(String externalId);
    
    /**
     * 사용자별 공연 ID로 이벤트 검색
     */
    List<Event> findByUserIdAndExternalId(Long userId, String externalId);
}
