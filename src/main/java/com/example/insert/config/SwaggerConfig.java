package com.example.insert.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("로컬 개발 서버"),
                        new Server().url("https://api.insert.com").description("프로덕션 서버")
                ));
    }

    @Bean
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper());
        return converter;
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private Info apiInfo() {
        return new Info()
                .title("InSert - AI 기반 장소 추천 시스템 API")
                .description("""
                    ## 🚀 InSert API 문서
                    
                    ### 주요 기능
                    - **AI 기반 맞춤형 장소 추천**: 사용자 프로필과 이동수단을 고려한 장소 추천
                    - **사용자 일정 관리**: 추천받은 장소를 개인 일정에 추가/관리
                    - **카테고리별 추천**: 엑티비티, 식사, 카페 등 카테고리별 장소 추천
                    - **리뷰 시스템**: 사진/동영상 첨부 가능한 리뷰 작성
                    
                    ### 사용 방법
                    1. **장소 추천**: POST `/api/recommendations/places`
                    2. **장소 상세정보**: GET `/api/recommendations/places/{placeId}`
                    3. **일정 추가**: POST `/api/schedules/places`
                    4. **일정 조회**: GET `/api/schedules/users/{userId}/events/{eventId}`
                    5. **파일 업로드**: POST `/api/files/upload` (단일) 또는 `/api/files/upload-multiple` (다중)
                    6. **리뷰 작성**: POST `/api/reviews` (파일 업로드 후 URL 사용)
                    
                    ### 테스트 데이터
                    - **이벤트 ID**: 1
                    - **사용자 프로필**: ALONE, COUPLE, FAMILY
                    - **이동수단**: WALK, CAR, BUS, SUBWAY
                    
                    ### 파일 업로드 가이드
                    - **지원 형식**: JPEG, PNG, GIF (이미지), MP4, AVI, MOV (동영상)
                    - **최대 크기**: 10MB per file
                    - **최대 개수**: 20개 (다중 업로드 시)
                    - **업로드 순서**: 1) 파일 업로드 → 2) URL 받기 → 3) 리뷰 작성 시 URL 사용
                    """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("InSert 개발팀")
                        .email("dev@insert.com")
                        .url("https://insert.com"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }
}
