package com.example.insert.service;

import com.example.insert.dto.PlaceRecommendationRequest;
import com.example.insert.dto.PlaceRecommendationResponse;
import com.example.insert.entity.RecommendedPlace;
import com.example.insert.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIRecommendationServiceTest {

    @Mock
    private KakaoMapService kakaoMapService;

    @Mock
    private HuggingFaceAIService huggingFaceAIService;

    @Mock
    private UserService userService;

    @Mock
    private EventService eventService;

    @InjectMocks
    private AIRecommendationService aiRecommendationService;

    @Test
    void testGenerateRecommendations_EachCategoryHasThreePlaces() {
        // Given
        PlaceRecommendationRequest request = PlaceRecommendationRequest.builder()
                .venueName("인천 문학경기장")
                .profileType(User.ProfileType.COUPLE)
                .transportationMethod(PlaceRecommendationRequest.TransportationMethod.WALK)
                .customConditions("분위기 있는 데이트 코스")
                .build();

        User user = User.builder()
                .id(1L)
                .name("테스트 사용자")
                .email("test@example.com")
                .profileType(User.ProfileType.COUPLE)
                .build();

        // Mock KakaoMapService
        List<RecommendedPlace> mockNearbyPlaces = createMockNearbyPlaces();
        when(kakaoMapService.searchNearbyPlacesByVenueName(anyString(), anyInt()))
                .thenReturn(mockNearbyPlaces);

        // Mock UserService
        when(userService.findById(anyLong())).thenReturn(user);

        // Mock HuggingFaceAIService
        List<RecommendedPlace> mockRecommendedPlaces = createMockRecommendedPlaces();
        when(huggingFaceAIService.getAIRecommendations(anyList(), any(), any()))
                .thenReturn(mockRecommendedPlaces);
        // lenient로 설정하여 불필요한 stubbing 경고 제거
        lenient().when(huggingFaceAIService.getAIRecommendationsWithRelaxedConditions(anyList(), any(), any(), any()))
                .thenReturn(createMockAdditionalPlaces());

        // When
        PlaceRecommendationResponse response = aiRecommendationService.generateRecommendations(request, user.getId());

        // Then
        assertNotNull(response);
        assertNotNull(response.getRecommendations());
        
        // 각 카테고리별로 정확히 3개씩 장소가 있는지 확인
        for (PlaceRecommendationResponse.CategoryRecommendation category : response.getRecommendations()) {
            assertNotNull(category.getPlaces());
            assertEquals(3, category.getPlaces().size(), 
                    "카테고리 " + category.getCategoryName() + "에 정확히 3개 장소가 있어야 합니다");
            
            // 각 장소가 유효한지 확인
            for (PlaceRecommendationResponse.PlaceInfo place : category.getPlaces()) {
                assertNotNull(place.getName());
                assertNotNull(place.getAddress());
                assertNotNull(place.getAiReason());
            }
        }
        
        // 총 9개 장소 (3개 카테고리 × 3개씩)
        int totalPlaces = response.getRecommendations().stream()
                .mapToInt(cat -> cat.getPlaces().size())
                .sum();
        assertEquals(9, totalPlaces, "총 9개 장소가 추천되어야 합니다");
    }

    private List<RecommendedPlace> createMockNearbyPlaces() {
        List<RecommendedPlace> places = new ArrayList<>();
        
        // 엑티비티 장소들
        for (int i = 1; i <= 20; i++) {
            places.add(RecommendedPlace.builder()
                    .id((long) i)
                    .name("엑티비티 장소 " + i)
                    .description("엑티비티 설명 " + i)
                    .category(RecommendedPlace.PlaceCategory.ACTIVITY)
                    .address("인천 주소 " + i)
                    .rating(4.0 + (i % 2) * 0.5)
                    .distanceFromVenue(0.5 + (i % 3) * 0.3)
                    .build());
        }
        
        // 식당 장소들
        for (int i = 21; i <= 40; i++) {
            places.add(RecommendedPlace.builder()
                    .id((long) i)
                    .name("식당 장소 " + i)
                    .description("식당 설명 " + i)
                    .category(RecommendedPlace.PlaceCategory.DINING)
                    .address("인천 주소 " + i)
                    .rating(4.0 + (i % 2) * 0.5)
                    .distanceFromVenue(0.5 + (i % 3) * 0.3)
                    .build());
        }
        
        // 카페 장소들
        for (int i = 41; i <= 60; i++) {
            places.add(RecommendedPlace.builder()
                    .id((long) i)
                    .name("카페 장소 " + i)
                    .description("카페 설명 " + i)
                    .category(RecommendedPlace.PlaceCategory.CAFE)
                    .address("인천 주소 " + i)
                    .rating(4.0 + (i % 2) * 0.5)
                    .distanceFromVenue(0.5 + (i % 3) * 0.3)
                    .build());
        }
        
        return places;
    }

    private List<RecommendedPlace> createMockRecommendedPlaces() {
        List<RecommendedPlace> places = new ArrayList<>();
        
        // 엑티비티 장소들
        for (int i = 1; i <= 5; i++) {
            places.add(RecommendedPlace.builder()
                    .id((long) i)
                    .name("추천 엑티비티 " + i)
                    .description("추천 엑티비티 설명 " + i)
                    .category(RecommendedPlace.PlaceCategory.ACTIVITY)
                    .address("인천 주소 " + i)
                    .rating(4.5)
                    .distanceFromVenue(0.5 + (i % 3) * 0.3)
                    .aiReason("COUPLE에서 즐길 수 있는 장소입니다")
                    .build());
        }
        
        // 식당 장소들
        for (int i = 6; i <= 10; i++) {
            places.add(RecommendedPlace.builder()
                    .id((long) i)
                    .name("추천 식당 " + i)
                    .description("추천 식당 설명 " + i)
                    .category(RecommendedPlace.PlaceCategory.DINING)
                    .address("인천 주소 " + i)
                    .rating(4.5)
                    .distanceFromVenue(0.5 + (i % 3) * 0.3)
                    .aiReason("COUPLE에서 즐길 수 있는 장소입니다")
                    .build());
        }
        
        // 카페 장소들
        for (int i = 11; i <= 15; i++) {
            places.add(RecommendedPlace.builder()
                    .id((long) i)
                    .name("추천 카페 " + i)
                    .description("추천 카페 설명 " + i)
                    .category(RecommendedPlace.PlaceCategory.CAFE)
                    .address("인천 주소 " + i)
                    .rating(4.5)
                    .distanceFromVenue(0.5 + (i % 3) * 0.3)
                    .aiReason("COUPLE에서 즐길 수 있는 장소입니다")
                    .build());
        }
        
        return places;
    }

    private List<RecommendedPlace> createMockAdditionalPlaces() {
        List<RecommendedPlace> places = new ArrayList<>();
        
        // 추가 엑티비티 장소들
        for (int i = 100; i <= 120; i++) {
            places.add(RecommendedPlace.builder()
                    .id((long) i)
                    .name("추가 엑티비티 " + i)
                    .description("추가 엑티비티 설명 " + i)
                    .category(RecommendedPlace.PlaceCategory.ACTIVITY)
                    .address("인천 주소 " + i)
                    .rating(4.0)
                    .distanceFromVenue(1.0 + (i % 3) * 0.5)
                    .aiReason("추가 추천 장소입니다")
                    .build());
        }
        
        // 추가 식당 장소들
        for (int i = 121; i <= 140; i++) {
            places.add(RecommendedPlace.builder()
                    .id((long) i)
                    .name("추가 식당 " + i)
                    .description("추가 식당 설명 " + i)
                    .category(RecommendedPlace.PlaceCategory.DINING)
                    .address("인천 주소 " + i)
                    .rating(4.0)
                    .distanceFromVenue(1.0 + (i % 3) * 0.5)
                    .aiReason("추가 추천 장소입니다")
                    .build());
        }
        
        // 추가 카페 장소들
        for (int i = 141; i <= 160; i++) {
            places.add(RecommendedPlace.builder()
                    .id((long) i)
                    .name("추가 카페 " + i)
                    .description("추가 카페 설명 " + i)
                    .category(RecommendedPlace.PlaceCategory.CAFE)
                    .address("인천 주소 " + i)
                    .rating(4.0)
                    .distanceFromVenue(1.0 + (i % 3) * 0.5)
                    .aiReason("추가 추천 장소입니다")
                    .build());
        }
        
        return places;
    }
}
