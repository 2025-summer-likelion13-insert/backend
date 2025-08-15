package com.example.insert.validation;

import com.example.insert.dto.PlaceRecommendationRequest;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.util.List;

@Component
public class PlaceRecommendationValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return PlaceRecommendationRequest.class.equals(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        PlaceRecommendationRequest request = (PlaceRecommendationRequest) target;
        
        // 장소명 검증
        if (request.getVenueName() == null || request.getVenueName().trim().isEmpty()) {
            errors.rejectValue("venueName", "invalid.venueName", "장소명을 입력해주세요.");
        }
        
        if (request.getVenueName() != null && request.getVenueName().length() > 100) {
            errors.rejectValue("venueName", "invalid.venueName", "장소명은 최대 100자까지 입력 가능합니다.");
        }
        
        // 프로필 타입 검증
        if (request.getProfileType() == null) {
            errors.rejectValue("profileType", "invalid.profileType", "프로필 타입을 선택해주세요.");
        }
        
        // 이동수단 검증
        validateTransportationMethod(request.getTransportationMethod(), errors);
        
        // 사용자 조건 검증
        validateCustomConditions(request.getCustomConditions(), errors);
    }
    
    private void validateTransportationMethod(PlaceRecommendationRequest.TransportationMethod method, Errors errors) {
        if (method == null) {
            errors.rejectValue("transportationMethod", "invalid.transportationMethod", "이동수단을 선택해주세요.");
        }
    }
    
    private void validateCustomConditions(String conditions, Errors errors) {
        if (conditions != null && conditions.length() > 50) {
            errors.rejectValue("customConditions", "invalid.customConditions", "사용자 조건은 최대 50자까지 입력 가능합니다.");
        }
        
        // 특수문자 검증 (기본적인 위험 문자만)
        if (conditions != null && conditions.matches(".*[<>\"'&].*")) {
            errors.rejectValue("customConditions", "invalid.customConditions", "특수문자 <, >, \", ', &는 사용할 수 없습니다.");
        }
    }
}
