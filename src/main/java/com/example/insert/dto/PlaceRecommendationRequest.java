package com.example.insert.dto;

import com.example.insert.entity.User.ProfileType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI 기반 장소 추천 요청 데이터")
public class PlaceRecommendationRequest {
    
    @NotBlank(message = "장소명은 필수입니다")
    @Schema(description = "행사장/기준 장소명", example = "인천 문학경기장")
    private String venueName;
    
    @NotNull(message = "Profile type is required")
    @Schema(description = "사용자 프로필 타입", example = "COUPLE")
    private ProfileType profileType;
    
    @NotNull(message = "Transportation method is required")
    @Schema(description = "이동 수단", example = "WALK")
    private TransportationMethod transportationMethod;
    
    @Size(max = 50, message = "Conditions cannot exceed 50 characters")
    @Schema(description = "커스텀 조건 (데이트 스타일, 선호사항 등)", example = "분위기 있는 데이트 코스")
    private String customConditions;
    
    @Schema(description = "이동 수단 종류")
    public enum TransportationMethod {
        @Schema(description = "도보") WALK,
        @Schema(description = "자동차") CAR,
        @Schema(description = "버스") BUS,
        @Schema(description = "지하철") SUBWAY
    }
}
