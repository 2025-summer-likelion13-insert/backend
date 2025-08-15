package com.example.insert.repository;

import com.example.insert.entity.UserSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserScheduleRepository extends JpaRepository<UserSchedule, Long> {
    
    /**
     * 특정 사용자의 특정 이벤트에 대한 일정 조회
     */
    List<UserSchedule> findByUserIdAndEventId(Long userId, Long eventId);
    
    /**
     * 특정 사용자의 모든 일정 조회
     */
    List<UserSchedule> findByUserId(Long userId);
    
    /**
     * 특정 이벤트의 모든 일정 조회
     */
    List<UserSchedule> findByEventId(Long eventId);
    
    /**
     * 특정 사용자의 특정 이벤트에 대한 모든 일정 삭제
     */
    void deleteByUserIdAndEventId(Long userId, Long eventId);
    
    /**
     * 특정 사용자의 특정 이벤트에 대한 특정 장소의 일정 조회
     */
    java.util.Optional<UserSchedule> findByUserIdAndEventIdAndPlaceId(Long userId, Long eventId, Long placeId);
}
