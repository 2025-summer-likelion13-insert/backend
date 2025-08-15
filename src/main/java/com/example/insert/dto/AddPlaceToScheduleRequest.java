package com.example.insert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddPlaceToScheduleRequest {
    
    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;
    
    @NotNull(message = "이벤트 ID는 필수입니다")
    private Long eventId;
    
    @NotNull(message = "장소 ID는 필수입니다")
    private Long placeId;
}
