package com.example.insert.service;

import com.example.insert.dto.PlaceRecommendationRequest;
import com.example.insert.dto.PlaceRecommendationResponse;
import com.example.insert.entity.Event;
import com.example.insert.entity.RecommendedPlace;
import com.example.insert.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIRecommendationService {

    private final KakaoMapService kakaoMapService;
    private final HuggingFaceAIService huggingFaceAIService;
    private final UserService userService;
    private final EventService eventService;

    @Value("${app.recommendation.max-places-per-category:3}")
    private int maxPlacesPerCategory = 3;
    
    // 추천된 장소들을 메모리에 저장 (실제로는 데이터베이스 사용)
    private final Map<Long, RecommendedPlace> recommendedPlacesCache = new ConcurrentHashMap<>();

    /**
     * AI 기반 장소 추천 생성
     */
    public PlaceRecommendationResponse generateRecommendations(PlaceRecommendationRequest request, Long userId) {
        log.info("AI 기반 장소 추천 시작: userId={}, request={}", userId, request);

        try {
            // 사용자 정보 조회
            User user = userService.findById(userId);
            if (user == null) {
                throw new RuntimeException("사용자를 찾을 수 없습니다: " + userId);
            }

            // 1단계: 카카오맵에서 주변 장소 검색
            List<RecommendedPlace> nearbyPlaces = kakaoMapService.searchNearbyPlacesByVenueName(
                    request.getVenueName(), 200); // 200개로 증가
            log.info("카카오맵 검색 완료: {}개 장소", nearbyPlaces.size());

            // 2단계: AI 점수 계산 및 카테고리 분류
            List<RecommendedPlace> aiScoredPlaces = huggingFaceAIService.getAIRecommendations(
                    nearbyPlaces, request, user);
            log.info("AI 점수 계산 완료: {}개 장소", aiScoredPlaces.size());

            // 3단계: 완벽한 중복 제거
            List<RecommendedPlace> uniquePlaces = removeDuplicatesCompletely(aiScoredPlaces);
            log.info("중복 제거 완료: {}개 -> {}개", aiScoredPlaces.size(), uniquePlaces.size());

            // 4단계: 카테고리별로 정확히 3개씩 보장
            Map<RecommendedPlace.PlaceCategory, List<RecommendedPlace>> finalPlacesByCategory = 
                    ensureExactlyThreePlacesPerCategory(uniquePlaces, request, user);
            
            log.info("카테고리별 3개 보장 완료: {}", 
                    finalPlacesByCategory.entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey, 
                                    e -> e.getValue().size())));

            // 5단계: 응답 생성
            return createRecommendationResponse(finalPlacesByCategory, user);
            
        } catch (Exception e) {
            log.error("AI 기반 장소 추천 생성 중 오류 발생", e);
            throw new RuntimeException("장소 추천 생성에 실패했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 완벽한 중복 제거 (이름 + 주소 기준)
     */
    private List<RecommendedPlace> removeDuplicatesCompletely(List<RecommendedPlace> places) {
        Map<String, RecommendedPlace> uniquePlaces = new LinkedHashMap<>();
        
        for (RecommendedPlace place : places) {
            if (place.getName() == null || place.getName().trim().isEmpty()) {
                continue;
            }
            
            String key = generateUniqueKey(place);
            if (!uniquePlaces.containsKey(key)) {
                uniquePlaces.put(key, place);
            } else {
                // 더 좋은 장소로 교체
                RecommendedPlace existing = uniquePlaces.get(key);
                if (isBetterPlace(place, existing)) {
                    uniquePlaces.put(key, place);
                    log.debug("중복 제거: {} -> {} (더 좋은 장소)", existing.getName(), place.getName());
                }
            }
        }
        
        log.info("완전한 중복 제거: {}개 -> {}개", places.size(), uniquePlaces.size());
        return new ArrayList<>(uniquePlaces.values());
    }

    /**
     * 장소의 고유 키 생성 (이름 + 주소)
     */
    private String generateUniqueKey(RecommendedPlace place) {
        String name = place.getName() != null ? place.getName().trim().toLowerCase() : "";
        String address = place.getAddress() != null ? place.getAddress().trim().toLowerCase() : "";
        return (name + "|" + address).replaceAll("\\s+", " ");
    }

    /**
     * 어떤 장소가 더 좋은지 판단
     */
    private boolean isBetterPlace(RecommendedPlace newPlace, RecommendedPlace existing) {
        // 평점이 더 높은 경우
        if (newPlace.getRating() != null && existing.getRating() != null) {
            if (newPlace.getRating() > existing.getRating()) {
                return true;
            } else if (newPlace.getRating() < existing.getRating()) {
                return false;
            }
        }
        
        // 평점이 같거나 없는 경우, 거리가 더 가까운 경우
        if (newPlace.getDistanceFromVenue() != null && existing.getDistanceFromVenue() != null) {
            return newPlace.getDistanceFromVenue() < existing.getDistanceFromVenue();
        }
        
        return false;
    }

    /**
     * 각 카테고리별로 정확히 3개씩 보장
     */
    private Map<RecommendedPlace.PlaceCategory, List<RecommendedPlace>> ensureExactlyThreePlacesPerCategory(
            List<RecommendedPlace> places, 
            PlaceRecommendationRequest request, 
            User user) {
        
        // 카테고리별로 그룹화
        Map<RecommendedPlace.PlaceCategory, List<RecommendedPlace>> groupedPlaces = 
                places.stream()
                        .collect(Collectors.groupingBy(RecommendedPlace::getCategory));
        
        log.info("카테고리별 그룹화 결과: {}", 
                groupedPlaces.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, 
                                e -> e.getValue().size())));
        
        // 카테고리 분류 수정 및 중복 제거
        Map<RecommendedPlace.PlaceCategory, List<RecommendedPlace>> correctedPlaces = 
                correctCategoryClassifications(groupedPlaces);
        
        Map<RecommendedPlace.PlaceCategory, List<RecommendedPlace>> result = new HashMap<>();
        
        // 각 카테고리별로 정확히 3개씩 처리
        for (RecommendedPlace.PlaceCategory category : RecommendedPlace.PlaceCategory.values()) {
            List<RecommendedPlace> categoryPlaces = correctedPlaces.getOrDefault(category, new ArrayList<>());
            
            log.info("카테고리 {} 처리 시작: 초기 {}개", category, categoryPlaces.size());
            
            // 3개 미만인 경우 추가 장소 검색
            if (categoryPlaces.size() < maxPlacesPerCategory) {
                int needed = maxPlacesPerCategory - categoryPlaces.size();
                log.info("카테고리 {}에 {}개 추가 장소 필요", category, needed);
            
                List<RecommendedPlace> additionalPlaces = searchAdditionalPlacesForCategory(
                        category, needed * 15, request, user); // 15배로 검색
                
                // 중복 제거 후 추가
                Set<String> existingKeys = categoryPlaces.stream()
                        .map(this::generateUniqueKey)
                        .collect(Collectors.toSet());
                
                List<RecommendedPlace> nonDuplicateAdditional = additionalPlaces.stream()
                        .filter(place -> !existingKeys.contains(generateUniqueKey(place)))
                        .limit(needed)
                        .collect(Collectors.toList());
                
                categoryPlaces.addAll(nonDuplicateAdditional);
                log.info("카테고리 {}에 {}개 추가 장소 추가 (총 {}개)", 
                        category, nonDuplicateAdditional.size(), categoryPlaces.size());
            }
            
            // 정확히 3개만 선택 (평점과 거리 기준)
            if (categoryPlaces.size() > maxPlacesPerCategory) {
                categoryPlaces = categoryPlaces.stream()
                        .sorted(Comparator
                                .comparing(RecommendedPlace::getRating, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(RecommendedPlace::getDistanceFromVenue, Comparator.nullsLast(Comparator.naturalOrder())))
                        .limit(maxPlacesPerCategory)
                        .collect(Collectors.toList());
                log.info("카테고리 {} 상위 {}개 장소 선택", category, maxPlacesPerCategory);
            }
            
            // 최종 검증: 정확히 3개인지 확인
            if (categoryPlaces.size() != maxPlacesPerCategory) {
                log.warn("카테고리 {}에 정확히 {}개 장소가 없습니다 (현재: {}개)", 
                        category, maxPlacesPerCategory, categoryPlaces.size());
                
                // 부족한 경우 더 많은 장소 검색
                if (categoryPlaces.size() < maxPlacesPerCategory) {
                    int additionalNeeded = (maxPlacesPerCategory - categoryPlaces.size()) * 25; // 25배로 검색
                    List<RecommendedPlace> morePlaces = searchAdditionalPlacesForCategory(
                            category, additionalNeeded, request, user);
                    
                    // 중복 제거 후 추가
                    Set<String> allExistingKeys = categoryPlaces.stream()
                            .map(this::generateUniqueKey)
                            .collect(Collectors.toSet());
                    
                    List<RecommendedPlace> nonDuplicateMore = morePlaces.stream()
                            .filter(place -> !allExistingKeys.contains(generateUniqueKey(place)))
                            .limit(maxPlacesPerCategory - categoryPlaces.size())
                            .collect(Collectors.toList());
                    
                    categoryPlaces.addAll(nonDuplicateMore);
                    log.info("카테고리 {} 추가 장소 검색 후: {}개", category, categoryPlaces.size());
                }
            }
            
            // 빈 카테고리가 아닌 경우만 결과에 추가
            if (!categoryPlaces.isEmpty()) {
                result.put(category, categoryPlaces);
                log.info("카테고리 {} 최종 완료: {}개", category, categoryPlaces.size());
            }
        }
        
        log.info("전체 카테고리 처리 완료: 총 {}개 카테고리", result.size());
        return result;
    }
    
    /**
     * 카테고리 분류 수정 및 중복 제거
     */
    private Map<RecommendedPlace.PlaceCategory, List<RecommendedPlace>> correctCategoryClassifications(
            Map<RecommendedPlace.PlaceCategory, List<RecommendedPlace>> groupedPlaces) {
        
        Map<RecommendedPlace.PlaceCategory, List<RecommendedPlace>> corrected = new HashMap<>();
        Set<String> usedPlaces = new HashSet<>();
        
        // 모든 장소를 수집하여 올바른 카테고리로 재분류
        List<RecommendedPlace> allPlaces = new ArrayList<>();
        for (List<RecommendedPlace> places : groupedPlaces.values()) {
            allPlaces.addAll(places);
        }
        
        log.info("전체 {}개 장소에 대해 카테고리 재분류 시작", allPlaces.size());
        
        // 각 장소를 올바른 카테고리로 분류
        for (RecommendedPlace place : allPlaces) {
            // 데이트에 부적절한 장소는 제외
            if (isInappropriateForDating(place.getName(), place.getDescription())) {
                log.debug("데이트에 부적절한 장소 제외: {}", place.getName());
                continue;
            }
            
            // 이미 사용된 장소는 건너뛰기
            String placeKey = generateUniqueKey(place);
            if (usedPlaces.contains(placeKey)) {
                continue;
            }
            
            // 올바른 카테고리 결정
            RecommendedPlace.PlaceCategory correctCategory = determineCorrectCategory(place);
            log.debug("장소 '{}' 카테고리 재분류: {} -> {}", place.getName(), place.getCategory(), correctCategory);
            
            // 해당 카테고리에 추가
            corrected.computeIfAbsent(correctCategory, k -> new ArrayList<>()).add(place);
            usedPlaces.add(placeKey);
        }
        
        log.info("카테고리 분류 수정 완료: {}", 
                corrected.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, 
                                e -> e.getValue().size())));
        
        return corrected;
    }

    /**
     * 장소의 올바른 카테고리 결정 (강제 재분류)
     */
    private RecommendedPlace.PlaceCategory determineCorrectCategory(RecommendedPlace place) {
        String name = place.getName() != null ? place.getName().toLowerCase() : "";
        String description = place.getDescription() != null ? place.getDescription().toLowerCase() : "";
        
        log.debug("장소 '{}' 카테고리 재분류 시작: 원래카테고리={}", place.getName(), place.getCategory());
        
        // 지하철역, 교통시설 등 데이트에 부적절한 장소 필터링
        if (isInappropriateForDating(name, description)) {
            log.debug("장소 '{}' 데이트 부적절로 제외", place.getName());
            return RecommendedPlace.PlaceCategory.ACTIVITY; // 임시로 ACTIVITY에 배치 (나중에 필터링)
        }
        
        // 카페 관련 키워드 (가장 먼저 확인)
        if (isCafePlace(name, description)) {
            log.debug("장소 '{}' -> CAFE로 분류", place.getName());
            return RecommendedPlace.PlaceCategory.CAFE;
        }
        
        // 음식점 관련 키워드
        if (isDiningPlace(name, description)) {
            log.debug("장소 '{}' -> DINING으로 분류", place.getName());
            return RecommendedPlace.PlaceCategory.DINING;
        }
        
        // 엑티비티 관련 키워드 (데이트에 적합한 장소만)
        if (isActivityPlace(name, description)) {
            log.debug("장소 '{}' -> ACTIVITY로 분류", place.getName());
            return RecommendedPlace.PlaceCategory.ACTIVITY;
        }
        
        // 기본값은 원래 카테고리
        log.debug("장소 '{}' -> 원래카테고리 {} 유지", place.getName(), place.getCategory());
        return place.getCategory();
    }

    /**
     * 데이트에 부적절한 장소인지 확인
     */
    private boolean isInappropriateForDating(String name, String description) {
        // 지하철역, 버스정류장 등 교통시설
        if (name.contains("역") || name.contains("정류장") || name.contains("터미널") ||
            description.contains("지하철") || description.contains("전철") || 
            description.contains("버스") || description.contains("교통")) {
            return true;
        }
        
        // 공공시설, 행정기관 등
        if (name.contains("시청") || name.contains("구청") || name.contains("동사무소") ||
            name.contains("우체국") || name.contains("은행") || name.contains("병원") ||
            description.contains("공공") || description.contains("행정") || 
            description.contains("기관")) {
            return true;
        }
        
        return false;
    }

    /**
     * 카페 장소인지 확인 (강력한 분류)
     */
    private boolean isCafePlace(String name, String description) {
        // 카페 관련 키워드 (정확한 매칭)
        if (name.contains("카페") || name.contains("커피") || name.contains("스타벅스") || 
            name.contains("투썸") || name.contains("할리스") || name.contains("이디야") ||
            name.contains("코피발리") || name.contains("브라더스커피") ||
            name.contains("엔제리너스") || name.contains("폴바셋") ||
            name.contains("메가mgc") || name.contains("셀프이스팀") ||
            name.contains("감성커피") || name.contains("레오커피") ||
            description.contains("카페") || description.contains("커피") ||
            description.contains("음료")) {
            log.debug("장소 '{}' 카페 키워드 매칭으로 CAFE 분류", name);
            return true;
        }
        
        // 음식문화거리, 맛집 등은 카페가 아님 - 명시적으로 제외
        if (name.contains("음식문화거리") || name.contains("맛집") || 
            name.contains("먹자골목") || name.contains("음식거리") ||
            description.contains("음식문화거리") || description.contains("맛집") ||
            description.contains("먹자골목") || description.contains("음식거리")) {
            log.debug("장소 '{}' 음식문화거리 키워드로 CAFE 제외", name);
            return false;
        }
        
        log.debug("장소 '{}' 카페 키워드 없음", name);
        return false;
    }

    /**
     * 음식점 장소인지 확인 (강력한 분류)
     */
    private boolean isDiningPlace(String name, String description) {
        // 음식점 관련 키워드
        if (name.contains("음식") || name.contains("맛집") || name.contains("식당") ||
            name.contains("레스토랑") || name.contains("한식") || name.contains("양식") ||
            name.contains("중식") || name.contains("일식") || name.contains("돈까스") ||
            name.contains("파니노") || name.contains("빅브라더") ||
            name.contains("고구려") || name.contains("한우") || name.contains("맥도날드") ||
            name.contains("고기매니아") || name.contains("토지장어") || name.contains("솔밭가든") ||
            name.contains("자담치킨") || name.contains("육심") ||
            description.contains("음식") || description.contains("맛집") || 
            description.contains("식당") || description.contains("양식") ||
            description.contains("한식") || description.contains("중식") ||
            description.contains("일식")) {
            log.debug("장소 '{}' 음식점 키워드 매칭으로 DINING 분류", name);
            return true;
        }
        
        // 음식문화거리, 맛집 등은 명시적으로 DINING에 포함
        if (name.contains("음식문화거리") || name.contains("맛집") || 
            name.contains("먹자골목") || name.contains("음식거리") ||
            description.contains("음식문화거리") || description.contains("맛집") ||
            description.contains("먹자골목") || description.contains("음식거리")) {
            log.debug("장소 '{}' 음식문화거리 키워드로 DINING 분류", name);
            return true;
        }
        
        // 카페는 DINING이 아님 - 명시적으로 제외
        if (name.contains("카페") || name.contains("커피") || name.contains("스타벅스") ||
            name.contains("투썸") || name.contains("할리스") || name.contains("이디야") ||
            name.contains("코피발리") || name.contains("브라더스커피") ||
            description.contains("카페") || description.contains("커피")) {
            log.debug("장소 '{}' 카페 키워드로 DINING 제외", name);
            return false;
        }
        
        log.debug("장소 '{}' 음식점 키워드 없음", name);
        return false;
    }

    /**
     * 엑티비티 장소인지 확인 (데이트에 적합한 장소만)
     */
    private boolean isActivityPlace(String name, String description) {
        // 데이트에 적합한 엑티비티 키워드
        if (name.contains("공원") || name.contains("산") || name.contains("박물관") ||
            name.contains("미술관") || name.contains("영화관") || name.contains("놀이공원") ||
            name.contains("스포츠") || name.contains("체험") || name.contains("문화") ||
            name.contains("거리") || name.contains("로데오") || name.contains("테마") ||
            description.contains("공원") || description.contains("산") || 
            description.contains("박물관") || description.contains("미술관") || 
            description.contains("영화관") || description.contains("놀이공원") ||
            description.contains("스포츠") || description.contains("체험") || 
            description.contains("문화") || description.contains("관광") ||
            description.contains("명소") || description.contains("테마거리")) {
            return true;
        }
        
        return false;
    }


    
    /**
     * 특정 카테고리에 추가 장소 검색
     */
    private List<RecommendedPlace> searchAdditionalPlacesForCategory(
            RecommendedPlace.PlaceCategory category, 
            int needed, 
            PlaceRecommendationRequest request, 
            User user) {
        
        List<RecommendedPlace> additionalPlaces = new ArrayList<>();
        
        try {
            // 카카오맵에서 추가 검색
            List<RecommendedPlace> extraPlaces = kakaoMapService.searchNearbyPlacesByVenueName(
                    request.getVenueName(), needed);
            
            // AI 점수 계산 (더 관대한 조건)
            List<RecommendedPlace> scoredPlaces = huggingFaceAIService.getAIRecommendationsWithRelaxedConditions(
                    extraPlaces, request, user, category);
            
            // 필요한 만큼만 추가
            for (int i = 0; i < Math.min(needed, scoredPlaces.size()); i++) {
                RecommendedPlace place = scoredPlaces.get(i);
                place.setCategory(category); // 카테고리 설정
                additionalPlaces.add(place);
            }
            
        } catch (Exception e) {
            log.warn("카테고리 {} 추가 장소 검색 실패: {}", category, e.getMessage());
        }
        
        return additionalPlaces;
    }
    
    /**
     * 추천 응답 생성
     */
    private PlaceRecommendationResponse createRecommendationResponse(
            Map<RecommendedPlace.PlaceCategory, List<RecommendedPlace>> placesByCategory, 
            User user) {
        
        List<PlaceRecommendationResponse.CategoryRecommendation> recommendations = new ArrayList<>();
        
        // 추천된 장소들을 캐시에 저장
        recommendedPlacesCache.clear(); // 기존 캐시 클리어
        
        for (Map.Entry<RecommendedPlace.PlaceCategory, List<RecommendedPlace>> entry : placesByCategory.entrySet()) {
            RecommendedPlace.PlaceCategory category = entry.getKey();
            List<RecommendedPlace> categoryPlaces = entry.getValue();
            
            if (!categoryPlaces.isEmpty()) {
                String categoryName = getCategoryDisplayName(category);
                List<PlaceRecommendationResponse.PlaceInfo> placeInfos = convertToPlaceInfo(categoryPlaces);
                
                // 각 장소를 캐시에 저장
                for (RecommendedPlace place : categoryPlaces) {
                    if (place.getId() != null) {
                        recommendedPlacesCache.put(place.getId(), place);
                        log.debug("장소 캐시에 저장: ID={}, 이름={}", place.getId(), place.getName());
                    }
                }
                
                recommendations.add(PlaceRecommendationResponse.CategoryRecommendation.builder()
                        .category(category)
                        .categoryName(categoryName)
                        .places(placeInfos)
                        .build());
            }
        }
        
        log.info("총 {}개 장소를 캐시에 저장 완료", recommendedPlacesCache.size());
        
        return PlaceRecommendationResponse.builder()
                .greeting(user.getName() + "님을 위한 오늘의 추천 장소 입니다.")
                .subtitle("인시트가 알려준 맞춤장소로 하루를 시작해보세요")
                .recommendations(recommendations)
                .build();
    }

    /**
     * RecommendedPlace를 PlaceInfo로 변환
     */
    private List<PlaceRecommendationResponse.PlaceInfo> convertToPlaceInfo(List<RecommendedPlace> places) {
        return places.stream()
                .map(place -> PlaceRecommendationResponse.PlaceInfo.builder()
                        .id(place.getId())
                        .name(place.getName())
                        .description(place.getDescription())
                        .imageUrl(place.getImageUrl())
                        .address(place.getAddress())
                        .latitude(place.getLatitude())      // 위도 추가
                        .longitude(place.getLongitude())    // 경도 추가
                        .rating(place.getRating())
                        .priceRange(place.getPriceRange())
                        .openingHours(place.getOpeningHours())
                        .aiReason(place.getAiReason())
                        .distanceFromVenue(place.getDistanceFromVenue())
                        .hasReview(null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 카테고리 표시 이름 반환
     */
    private String getCategoryDisplayName(RecommendedPlace.PlaceCategory category) {
        switch (category) {
            case ACTIVITY:
                return "엑티비티 추천";
            case DINING:
                return "식사 장소 추천";
            case CAFE:
                return "카페 장소 추천";
            default:
                return "기타 추천";
        }
    }

    /**
     * 장소 ID로 장소 상세 정보 조회
     */
    public PlaceRecommendationResponse.PlaceInfo getPlaceDetails(Long placeId) {
        log.info("장소 상세 정보 조회: placeId={}", placeId);
        
        try {
            // 캐시에서 장소 조회
            RecommendedPlace cachedPlace = recommendedPlacesCache.get(placeId);
            if (cachedPlace != null) {
                log.info("캐시에서 장소 조회 성공: ID={}, 이름={}", placeId, cachedPlace.getName());
                return PlaceRecommendationResponse.PlaceInfo.builder()
                        .id(cachedPlace.getId())
                        .name(cachedPlace.getName())
                        .description(cachedPlace.getDescription())
                        .imageUrl(cachedPlace.getImageUrl())
                        .address(cachedPlace.getAddress())
                        .latitude(cachedPlace.getLatitude())      // 위도 추가
                        .longitude(cachedPlace.getLongitude())    // 경도 추가
                        .rating(cachedPlace.getRating())
                        .priceRange(cachedPlace.getPriceRange())
                        .openingHours(cachedPlace.getOpeningHours())
                        .aiReason(cachedPlace.getAiReason())
                        .distanceFromVenue(cachedPlace.getDistanceFromVenue())
                        .hasReview(null)
                        .build();
            }
            
            // 캐시에 없는 경우 더미 데이터 반환
            log.warn("캐시에서 장소를 찾을 수 없음: ID={}, 캐시 크기={}", placeId, recommendedPlacesCache.size());
            return PlaceRecommendationResponse.PlaceInfo.builder()
                    .id(placeId)
                    .name("장소 " + placeId + " (캐시에 없음)")
                    .description("장소 상세 설명 - ID: " + placeId + " (캐시에서 찾을 수 없음)")
                    .imageUrl(null)
                    .address("인천 문학경기장 근처")
                    .rating(4.0)
                    .priceRange("정보 없음")
                    .openingHours("상세정보 확인")
                    .aiReason("캐시에서 찾을 수 없는 장소입니다.")
                    .distanceFromVenue(0.5)
                    .hasReview(null)
                    .build();
                    
        } catch (Exception e) {
            log.error("장소 상세 정보 조회 중 오류 발생: placeId={}", placeId, e);
            return null;
        }
    }
}
