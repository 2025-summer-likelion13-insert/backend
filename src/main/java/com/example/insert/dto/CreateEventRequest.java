package com.example.insert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {
    
    @NotBlank(message = "이벤트명은 필수입니다")
    @Size(max = 100, message = "이벤트명은 100자를 초과할 수 없습니다")
    private String name;
    
    @Size(max = 500, message = "설명은 500자를 초과할 수 없습니다")
    private String description;
    
    @NotNull(message = "이벤트 날짜는 필수입니다")
    private LocalDateTime eventDate;
    
    @NotBlank(message = "장소명은 필수입니다")
    @Size(max = 100, message = "장소명은 100자를 초과할 수 없습니다")
    private String venueName;
    
    @NotBlank(message = "장소 주소는 필수입니다")
    @Size(max = 200, message = "장소 주소는 200자를 초과할 수 없습니다")
    private String venueAddress;
    
    private Double venueLatitude;
    
    private Double venueLongitude;
    
    @Size(max = 50, message = "카테고리는 50자를 초과할 수 없습니다")
    private String category;
    
    private String imageUrl;
    
    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;
    
    private String externalId;  // 공연 ID와 연결 (선택사항)
}
