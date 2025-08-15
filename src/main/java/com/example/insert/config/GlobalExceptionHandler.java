package com.example.insert.config;

import com.example.insert.dto.ApiResponse;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * JSON 파싱 에러 처리
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<String>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e) {
        
        String errorMessage = "잘못된 JSON 형식입니다.";
        
        // 구체적인 에러 메시지 추출
        if (e.getCause() instanceof JsonParseException) {
            JsonParseException jpe = (JsonParseException) e.getCause();
            errorMessage = "JSON 파싱 오류: " + jpe.getOriginalMessage();
        } else if (e.getCause() instanceof JsonMappingException) {
            JsonMappingException jme = (JsonMappingException) e.getCause();
            errorMessage = "JSON 매핑 오류: " + jme.getOriginalMessage();
        }
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(errorMessage));
    }

    /**
     * 유효성 검증 에러 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<ApiResponse.ValidationError>>> handleValidationException(
            MethodArgumentNotValidException e) {
        
        List<ApiResponse.ValidationError> validationErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiResponse.ValidationError(
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getCode()))
                .collect(Collectors.toList());
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.validationError(validationErrors));
    }

    /**
     * 바인딩 에러 처리
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<List<ApiResponse.ValidationError>>> handleBindException(
            BindException e) {
        
        List<ApiResponse.ValidationError> validationErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiResponse.ValidationError(
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getCode()))
                .collect(Collectors.toList());
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.validationError(validationErrors));
    }

    /**
     * 메서드 인자 타입 불일치 에러 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<String>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        
        String errorMessage = String.format("잘못된 타입의 인자입니다: %s=%s", e.getName(), e.getValue());
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(errorMessage));
    }

    /**
     * 일반적인 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGenericException(Exception e) {
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("서버 내부 오류가 발생했습니다."));
    }
}
