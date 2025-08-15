package com.example.insert.service;

import com.example.insert.entity.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    /**
     * 기본 이벤트 서비스
     * 현재는 AI 추천 기능에서 직접 사용하지 않지만 향후 확장을 위해 생성
     */
    
    public Event findById(Long eventId) {
        // TODO: EventRepository 구현 후 실제 조회 로직 추가
        log.info("이벤트 조회 요청: {}", eventId);
        return null;
    }
}
