package com.example.insert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    
    private Long id;
    private Long userId;
    private String externalId;  // 공연 ID와 연결
    private String name;
    private String description;
    private LocalDateTime eventDate;
    private String venueName;
    private String venueAddress;
    private Double venueLatitude;
    private Double venueLongitude;
    private String category;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
