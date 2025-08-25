package com.example.insert.service;

import com.example.insert.dto.CreateEventRequest;
import com.example.insert.dto.UpdateEventRequest;
import com.example.insert.dto.EventResponse;
import com.example.insert.entity.Event;
import com.example.insert.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;

    /**
     * 이벤트 생성
     */
    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {
        log.info("이벤트 생성 요청: {}", request.getName());
        
        Event event = Event.builder()
                .userId(request.getUserId())
                .externalId(request.getExternalId())
                .name(request.getName())
                .description(request.getDescription())
                .eventDate(request.getEventDate())
                .venueName(request.getVenueName())
                .venueAddress(request.getVenueAddress())
                .venueLatitude(request.getVenueLatitude())
                .venueLongitude(request.getVenueLongitude())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .build();
        
        Event savedEvent = eventRepository.save(event);
        log.info("이벤트 생성 완료: ID={}", savedEvent.getId());
        
        return convertToEventResponse(savedEvent);
    }

    /**
     * 이벤트 조회 (ID로)
     */
    public EventResponse findById(Long eventId) {
        log.info("이벤트 조회 요청: {}", eventId);
        
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다: " + eventId));
        
        return convertToEventResponse(event);
    }

    /**
     * 모든 이벤트 조회
     */
    public List<EventResponse> findAllEvents() {
        log.info("모든 이벤트 조회 요청");
        
        List<Event> events = eventRepository.findAll();
        return events.stream()
                .map(this::convertToEventResponse)
                .collect(Collectors.toList());
    }

    /**
     * 특정 사용자의 이벤트 조회
     */
    public List<EventResponse> findEventsByUserId(Long userId) {
        log.info("사용자 이벤트 조회 요청: userId={}", userId);
        
        List<Event> events = eventRepository.findByUserId(userId);
        return events.stream()
                .map(this::convertToEventResponse)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리별 이벤트 조회
     */
    public List<EventResponse> findEventsByCategory(String category) {
        log.info("카테고리별 이벤트 조회 요청: category={}", category);
        
        List<Event> events = eventRepository.findByCategory(category);
        return events.stream()
                .map(this::convertToEventResponse)
                .collect(Collectors.toList());
    }

    /**
     * 이벤트명으로 검색
     */
    public List<EventResponse> searchEventsByName(String name) {
        log.info("이벤트명 검색 요청: name={}", name);
        
        List<Event> events = eventRepository.findByNameContainingIgnoreCase(name);
        return events.stream()
                .map(this::convertToEventResponse)
                .collect(Collectors.toList());
    }

    /**
     * 공연 ID로 이벤트 검색
     */
    public List<EventResponse> findEventsByExternalId(String externalId) {
        log.info("공연 ID로 이벤트 검색 요청: externalId={}", externalId);
        
        List<Event> events = eventRepository.findByExternalId(externalId);
        return events.stream()
                .map(this::convertToEventResponse)
                .collect(Collectors.toList());
    }

    /**
     * 사용자별 공연 ID로 이벤트 검색
     */
    public List<EventResponse> findEventsByUserIdAndExternalId(Long userId, String externalId) {
        log.info("사용자별 공연 ID로 이벤트 검색 요청: userId={}, externalId={}", userId, externalId);
        
        List<Event> events = eventRepository.findByUserIdAndExternalId(userId, externalId);
        return events.stream()
                .map(this::convertToEventResponse)
                .collect(Collectors.toList());
    }

    /**
     * 이벤트 수정
     */
    @Transactional
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request) {
        log.info("이벤트 수정 요청: eventId={}", eventId);
        
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("이벤트를 찾을 수 없습니다: " + eventId));
        
        // 부분 업데이트 (null이 아닌 필드만 업데이트)
        if (request.getName() != null) {
            event.setName(request.getName());
        }
        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }
        if (request.getEventDate() != null) {
            event.setEventDate(request.getEventDate());
        }
        if (request.getVenueName() != null) {
            event.setVenueName(request.getVenueName());
        }
        if (request.getVenueAddress() != null) {
            event.setVenueAddress(request.getVenueAddress());
        }
        if (request.getVenueLatitude() != null) {
            event.setVenueLatitude(request.getVenueLatitude());
        }
        if (request.getVenueLongitude() != null) {
            event.setVenueLongitude(request.getVenueLongitude());
        }
        if (request.getCategory() != null) {
            event.setCategory(request.getCategory());
        }
        if (request.getImageUrl() != null) {
            event.setImageUrl(request.getImageUrl());
        }
        if (request.getExternalId() != null) {
            event.setExternalId(request.getExternalId());
        }
        
        Event updatedEvent = eventRepository.save(event);
        log.info("이벤트 수정 완료: ID={}", updatedEvent.getId());
        
        return convertToEventResponse(updatedEvent);
    }

    /**
     * 이벤트 삭제
     */
    @Transactional
    public void deleteEvent(Long eventId) {
        log.info("이벤트 삭제 요청: eventId={}", eventId);
        
        if (!eventRepository.existsById(eventId)) {
            throw new IllegalArgumentException("이벤트를 찾을 수 없습니다: " + eventId);
        }
        
        eventRepository.deleteById(eventId);
        log.info("이벤트 삭제 완료: ID={}", eventId);
    }

    /**
     * Event를 EventResponse로 변환
     */
    private EventResponse convertToEventResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .userId(event.getUserId())
                .externalId(event.getExternalId())
                .name(event.getName())
                .description(event.getDescription())
                .eventDate(event.getEventDate())
                .venueName(event.getVenueName())
                .venueAddress(event.getVenueAddress())
                .venueLatitude(event.getVenueLatitude())
                .venueLongitude(event.getVenueLongitude())
                .category(event.getCategory())
                .imageUrl(event.getImageUrl())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
