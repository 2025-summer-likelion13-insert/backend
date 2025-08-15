package com.example.insert.service;

import com.example.insert.dto.PlaceRecommendationRequest;
import com.example.insert.entity.RecommendedPlace;
import com.example.insert.entity.User;
import com.example.insert.entity.User.ProfileType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class HuggingFaceAIService {

    @Value("${huggingface.api.key}")
    private String huggingFaceApiKey;

    @Value("${huggingface.model.url}")
    private String modelUrl;

    private final WebClient webClient;

    public HuggingFaceAIService() {
        this.webClient = WebClient.builder()
                .defaultHeader("Authorization", "Bearer " + huggingFaceApiKey)
                .build();
    }

    /**
     * AI 기반 장소 추천 및 순위 결정
     */
    public List<RecommendedPlace> getAIRecommendations(
            List<RecommendedPlace> allPlaces,
            PlaceRecommendationRequest request,
            User user) {
        
        try {
            // AI 프롬프트 생성
            String prompt = buildRecommendationPrompt(allPlaces, request, user);
            
            // Hugging Face API 호출
            String aiResponse = callHuggingFaceAPI(prompt);
            
            // AI 응답을 바탕으로 장소 순위 결정
            return rankPlacesByAI(allPlaces, aiResponse, request);
            
        } catch (Exception e) {
            log.error("Hugging Face AI 추천 중 오류 발생", e);
            // AI 실패 시 기본 추천 반환
            return getDefaultRecommendations(allPlaces, request);
        }
    }

    /**
     * AI 프롬프트 생성
     */
    private String buildRecommendationPrompt(
            List<RecommendedPlace> places,
            PlaceRecommendationRequest request,
            User user) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("=== 사용자 정보 ===\n");
        prompt.append("프로필: ").append(getProfileTypeKorean(request.getProfileType())).append("\n");
        prompt.append("이동수단: ").append(getTransportationKorean(request.getTransportationMethod())).append("\n");
        prompt.append("추가 조건: ").append(request.getCustomConditions() != null ? request.getCustomConditions() : "없음").append("\n\n");
        
        prompt.append("=== 장소 정보 ===\n");
        prompt.append("장소명: ").append(request.getVenueName()).append("\n");
        prompt.append("검색 반경: 2km\n\n");
        
        prompt.append("=== 추천할 장소 목록 ===\n");
        for (int i = 0; i < places.size(); i++) {
            RecommendedPlace place = places.get(i);
            prompt.append(i + 1).append(". ").append(place.getName())
                    .append(" (").append(place.getCategory()).append(")")
                    .append(" - ").append(place.getDescription())
                    .append(" - 거리: ").append(place.getDistanceFromVenue()).append("km\n");
        }
        
        prompt.append("\n=== AI 추천 요청 ===\n");
        prompt.append("위 장소들을 사용자 정보와 행사 정보를 고려하여 다음 기준으로 순위를 매겨주세요:\n");
        prompt.append("1. 사용자 프로필 적합성 (혼자/커플/가족)\n");
        prompt.append("2. 이동수단 고려 (도보/대중교통/차)\n");
        prompt.append("3. 행사 전후 시간 활용도\n");
        prompt.append("4. 거리 및 접근성\n");
        prompt.append("5. 사용자 추가 조건 만족도\n\n");
        
        // 사용자 추가 조건을 강조
        if (request.getCustomConditions() != null && !request.getCustomConditions().trim().isEmpty()) {
            prompt.append("⚠️ 중요: 사용자가 요청한 추가 조건을 최우선으로 고려하세요!\n");
            prompt.append("사용자 추가 조건: ").append(request.getCustomConditions()).append("\n");
            prompt.append("이 조건에 가장 적합한 장소를 상위에 배치해주세요.\n\n");
        }
        
        prompt.append("응답 형식: 장소 번호를 우선순위 순으로 나열 (예: 3,1,5,2,4)\n");
        
        return prompt.toString();
    }

    /**
     * Hugging Face API 호출
     */
    private String callHuggingFaceAPI(String prompt) {
        try {
            HuggingFaceRequest request = new HuggingFaceRequest(prompt);
            
            Mono<HuggingFaceResponse> response = webClient.post()
                    .uri(modelUrl)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(HuggingFaceResponse.class);
            
            HuggingFaceResponse result = response.block();
            
            if (result != null && result.generated_text != null && result.generated_text.length > 0) {
                return result.generated_text[0].generated_text;
            }
            
        } catch (Exception e) {
            log.error("Hugging Face API 호출 중 오류 발생", e);
        }
        
        return "";
    }

    /**
     * AI 응답을 바탕으로 장소 순위 결정 - customConditions 반영
     */
    private List<RecommendedPlace> rankPlacesByAI(
            List<RecommendedPlace> allPlaces,
            String aiResponse,
            PlaceRecommendationRequest request) {
        
        try {
            // AI 응답에서 장소 번호 추출
            List<Integer> rankedIndices = extractRankedIndices(aiResponse);
            
            // customConditions가 있는 경우 조건 만족도 점수 계산
            if (request.getCustomConditions() != null && !request.getCustomConditions().trim().isEmpty()) {
                return rankPlacesWithCustomConditions(allPlaces, rankedIndices, request);
            }
            
            // 기존 AI 순위대로 장소 재정렬
            return rankPlacesByAIOnly(allPlaces, rankedIndices);
            
        } catch (Exception e) {
            log.error("AI 순위 결정 중 오류 발생", e);
            return getDefaultRecommendations(allPlaces, request);
        }
    }
    
    /**
     * customConditions를 고려한 장소 순위 결정 - 더욱 정교한 필터링
     */
    private List<RecommendedPlace> rankPlacesWithCustomConditions(
            List<RecommendedPlace> allPlaces,
            List<Integer> aiRankedIndices,
            PlaceRecommendationRequest request) {
        
        String conditions = request.getCustomConditions().toLowerCase();
        String profileType = request.getProfileType().toString().toLowerCase();
        
        // 1단계: 조건에 맞는 장소만 우선 필터링
        List<RecommendedPlace> matchingPlaces = allPlaces.stream()
                .filter(place -> isPlaceMatchingConditions(place, conditions, profileType))
                .collect(Collectors.toList());
        
        // 2단계: 조건에 맞는 장소가 부족하면 추가 필터링
        if (matchingPlaces.size() < 3) {
            List<RecommendedPlace> additionalPlaces = allPlaces.stream()
                    .filter(place -> !matchingPlaces.contains(place))
                    .filter(place -> isPlacePartiallyMatchingConditions(place, conditions, profileType))
                    .collect(Collectors.toList());
            
            // 추가 장소들을 조건 만족도에 따라 정렬
            additionalPlaces.sort((a, b) -> Double.compare(
                    calculatePartialMatchScore(b, conditions, profileType),
                    calculatePartialMatchScore(a, conditions, profileType)
            ));
            
            // 최대 2개까지 추가
            matchingPlaces.addAll(additionalPlaces.stream().limit(2).collect(Collectors.toList()));
        }
        
        // 3단계: 조건에 맞는 장소가 여전히 부족하면 기본 추천
        if (matchingPlaces.size() < 3) {
            List<RecommendedPlace> defaultPlaces = allPlaces.stream()
                    .filter(place -> !matchingPlaces.contains(place))
                    .limit(5 - matchingPlaces.size())
                    .collect(Collectors.toList());
            
            matchingPlaces.addAll(defaultPlaces);
        }
        
        // 4단계: 최종 점수 계산 및 정렬
        List<PlaceWithScore> placesWithScores = new ArrayList<>();
        for (RecommendedPlace place : matchingPlaces) {
            double score = calculateFinalScore(place, conditions, profileType, aiRankedIndices);
            placesWithScores.add(new PlaceWithScore(place, score, allPlaces.indexOf(place)));
        }
        
        // 점수 순으로 정렬 (높은 점수 우선)
        placesWithScores.sort((a, b) -> Double.compare(b.score, a.score));
        
        // 점수 로그 출력 (디버깅용)
        log.info("=== 최종 장소 점수 순위 ===");
        for (int i = 0; i < Math.min(5, placesWithScores.size()); i++) {
            PlaceWithScore pws = placesWithScores.get(i);
            log.info("{}. {} - 점수: {:.1f} (조건만족: {}, 거리: {}km)", 
                    i + 1, 
                    pws.getPlace().getName(),
                    pws.getScore(),
                    isPlaceMatchingConditions(pws.getPlace(), conditions, profileType) ? "O" : "P",
                    pws.getPlace().getDistanceFromVenue());
        }
        
        // 5단계: 추천 이유 생성 및 반환
        List<RecommendedPlace> rankedPlaces = new ArrayList<>();
        for (PlaceWithScore pws : placesWithScores) {
            RecommendedPlace place = pws.getPlace();
            
            // AI 추천 이유 생성
            String aiReason = generateDetailedRecommendationReason(place, conditions, profileType);
            place.setAiReason(aiReason);
            
            rankedPlaces.add(place);
        }
        
        return rankedPlaces;
    }
    
    /**
     * 조건 만족도 점수 계산 - 더욱 정교한 점수 시스템
     */
    private double calculateConditionScore(RecommendedPlace place, String conditions, int aiRank) {
        double score = 0.0;
        
        // 1순위: 사용자 조건 만족도 (매우 중요!) - 가중치 증가
        if (isPlaceMatchingConditions(place, conditions, "alone")) { // 기본값으로 "alone" 사용
            score += 80.0; // 조건 만족 시 80점 추가 (기존 50점에서 증가)
            
            // 상세 조건 점수 추가
            double conditionMatchScore = calculateDetailedConditionScore(place, conditions);
            score += conditionMatchScore;
        }
        
        // 2순위: 거리 점수 (가장 중요!)
        if (place.getDistanceFromVenue() <= 0.5) {
            score += 40.0; // 0.5km 이내: 40점 (매우 높음)
        } else if (place.getDistanceFromVenue() <= 1.0) {
            score += 30.0; // 1km 이내: 30점 (높음)
        } else if (place.getDistanceFromVenue() <= 1.5) {
            score += 20.0; // 1.5km 이내: 20점 (보통)
        } else if (place.getDistanceFromVenue() <= 2.0) {
            score += 10.0; // 2km 이내: 10점 (낮음)
        }
        
        // 3순위: AI 순위 점수
        if (aiRank >= 0) {
            score += (10.0 - aiRank) * 1.0; // AI 순위 1위: 9점, 2위: 8점...
        }
        
        // 4순위: 카테고리별 기본 점수
        switch (place.getCategory()) {
            case ACTIVITY -> score += 2.0;
            case DINING -> score += 2.0;
            case CAFE -> score += 2.0;
        }
        
        return score;
    }
    
    /**
     * 상세 조건 만족도 점수 계산 - 더 정교한 매칭
     */
    private double calculateDetailedConditionScore(RecommendedPlace place, String conditions) {
        double detailedScore = 0.0;
        String placeInfo = (place.getName() + " " + place.getDescription() + " " + place.getAddress()).toLowerCase();
        
        // 데이트/로맨틱 조건 - 매우 정교한 매칭
        if (conditions.contains("데이트") || conditions.contains("로맨틱") || conditions.contains("분위기")) {
            // 데이트 코스 관련 키워드가 있으면 더 높은 점수
            if (conditions.contains("코스") || conditions.contains("데이트")) {
                if (placeInfo.contains("와인") || placeInfo.contains("스테이크") || placeInfo.contains("이탈리안") || 
                    placeInfo.contains("프렌치") || placeInfo.contains("일식") || placeInfo.contains("고급")) {
                    detailedScore += 20.0; // 데이트 코스에 최적: 20점
                }
                if (placeInfo.contains("갤러리") || placeInfo.contains("박물관") || placeInfo.contains("문화")) {
                    detailedScore += 18.0; // 문화 공간: 18점
                }
                if (placeInfo.contains("공원") || placeInfo.contains("산책") || placeInfo.contains("자연")) {
                    detailedScore += 15.0; // 자연 공간: 15점
                }
            } else {
                // 일반적인 데이트/로맨틱 조건
                if (placeInfo.contains("와인") || placeInfo.contains("스테이크") || placeInfo.contains("이탈리안") || 
                    placeInfo.contains("프렌치") || placeInfo.contains("일식") || placeInfo.contains("고급")) {
                    detailedScore += 15.0; // 고급 요리점
                }
                if (placeInfo.contains("갤러리") || placeInfo.contains("박물관") || placeInfo.contains("문화")) {
                    detailedScore += 12.0; // 문화 공간
                }
                if (placeInfo.contains("공원") || placeInfo.contains("산책") || placeInfo.contains("자연")) {
                    detailedScore += 10.0; // 자연 공간
                }
            }
        }
        
        // 혼자/조용한 곳 조건 - 집중도에 따른 점수
        if (conditions.contains("혼자") || conditions.contains("조용") || conditions.contains("집중")) {
            if (placeInfo.contains("도서관") || placeInfo.contains("독서")) {
                detailedScore += 15.0; // 최고의 집중 공간
            }
            if (placeInfo.contains("카페") && !placeInfo.contains("노래방") && !placeInfo.contains("게임")) {
                detailedScore += 12.0; // 조용한 카페
            }
            if (placeInfo.contains("공원") || placeInfo.contains("산책")) {
                detailedScore += 10.0; // 자연 속 휴식
            }
        }
        
        // 가족/아이 조건 - 안전성과 재미에 따른 점수
        if (conditions.contains("가족") || conditions.contains("아이") || conditions.contains("어린이")) {
            if (placeInfo.contains("놀이터") || placeInfo.contains("놀이공원")) {
                detailedScore += 15.0; // 아이들이 가장 좋아하는 곳
            }
            if (placeInfo.contains("동물원") || placeInfo.contains("박물관")) {
                detailedScore += 12.0; // 교육적 가치
            }
            if (placeInfo.contains("공원") || placeInfo.contains("스포츠")) {
                detailedScore += 10.0; // 가족 활동
            }
        }
        
        // 맛집/음식 조건 - 맛의 품질에 따른 점수
        if (conditions.contains("맛집") || conditions.contains("음식") || conditions.contains("맛")) {
            if (placeInfo.contains("미쉐린") || placeInfo.contains("고급") || placeInfo.contains("스테이크")) {
                detailedScore += 15.0; // 최고급 음식점
            }
            if (placeInfo.contains("해산물") || placeInfo.contains("고기") || placeInfo.contains("파스타")) {
                detailedScore += 12.0; // 특별한 요리
            }
            if (placeInfo.contains("디저트") || placeInfo.contains("베이커리")) {
                detailedScore += 10.0; // 달콤한 디저트
            }
        }
        
        return detailedScore;
    }
    
    /**
     * AI 순위만으로 장소 정렬
     */
    private List<RecommendedPlace> rankPlacesByAIOnly(List<RecommendedPlace> allPlaces, List<Integer> rankedIndices) {
        List<RecommendedPlace> rankedPlaces = new ArrayList<>();
        
        // AI 순위대로 장소 재정렬
        for (Integer index : rankedIndices) {
            if (index >= 0 && index < allPlaces.size()) {
                rankedPlaces.add(allPlaces.get(index));
            }
        }
        
        // AI 순위에 포함되지 않은 장소들 추가
        for (int i = 0; i < allPlaces.size(); i++) {
            if (!rankedIndices.contains(i)) {
                rankedPlaces.add(allPlaces.get(i));
            }
        }
        
        return rankedPlaces;
    }
    
    /**
     * 점수와 함께 장소를 담는 클래스
     */
    private static class PlaceWithScore {
        final RecommendedPlace place;
        final double score;
        final int originalIndex;
        
        PlaceWithScore(RecommendedPlace place, double score, int originalIndex) {
            this.place = place;
            this.score = score;
            this.originalIndex = originalIndex;
        }
        
        // Getter 메서드들 추가
        public RecommendedPlace getPlace() {
            return place;
        }
        
        public double getScore() {
            return score;
        }
        
        public int getOriginalIndex() {
            return originalIndex;
        }
    }

    /**
     * AI 응답에서 장소 번호 추출
     */
    private List<Integer> extractRankedIndices(String aiResponse) {
        List<Integer> indices = new ArrayList<>();
        
        try {
            // 숫자 패턴 찾기
            String[] parts = aiResponse.split("[,\\s]+");
            for (String part : parts) {
                try {
                    int index = Integer.parseInt(part.trim()) - 1; // 1-based to 0-based
                    if (index >= 0) {
                        indices.add(index);
                    }
                } catch (NumberFormatException ignored) {
                    // 숫자가 아닌 부분 무시
                }
            }
        } catch (Exception e) {
            log.warn("AI 응답 파싱 중 오류 발생: {}", aiResponse);
        }
        
        return indices;
    }

    /**
     * 기본 추천 (AI 실패 시) - customConditions 반영
     */
    private List<RecommendedPlace> getDefaultRecommendations(
            List<RecommendedPlace> places,
            PlaceRecommendationRequest request) {
        
        // customConditions가 있는 경우 우선순위 적용
        if (request.getCustomConditions() != null && !request.getCustomConditions().trim().isEmpty()) {
            return getCustomConditionsBasedRecommendations(places, request);
        }
        
        // 기존 카테고리별 기본 추천
        return getCategoryBasedRecommendations(places);
    }
    
    /**
     * customConditions 기반 추천
     */
    private List<RecommendedPlace> getCustomConditionsBasedRecommendations(
            List<RecommendedPlace> places,
            PlaceRecommendationRequest request) {
        
        String conditions = request.getCustomConditions().toLowerCase();
        List<RecommendedPlace> result = new ArrayList<>();
        
        // 조건에 맞는 장소들을 우선 선택
        List<RecommendedPlace> matchingPlaces = places.stream()
                .filter(place -> isPlaceMatchingConditions(place, conditions, "alone")) // 기본값으로 "alone" 사용
                .collect(Collectors.toList());
        
        // 조건에 맞는 장소가 있으면 우선 추가
        if (!matchingPlaces.isEmpty()) {
            for (RecommendedPlace place : matchingPlaces.stream().limit(3).collect(Collectors.toList())) {
                place.setAiReason(generateRecommendationReason(place, conditions));
                result.add(place);
            }
        }
        
        // 나머지는 카테고리별로 추가
        List<RecommendedPlace> categoryPlaces = getCategoryBasedRecommendations(places);
        for (RecommendedPlace place : categoryPlaces) {
            if (place.getAiReason() == null) {
                place.setAiReason("카테고리별 추천 장소입니다.");
            }
        }
        result.addAll(categoryPlaces);
        
        // 중복 제거 및 최대 5개로 제한
        return result.stream()
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }
    
    /**
     * 장소가 조건에 맞는지 확인 - 프로필 타입까지 고려한 정교한 매칭
     */
    private boolean isPlaceMatchingConditions(RecommendedPlace place, String conditions, String profileType) {
        String placeInfo = (place.getName() + " " + place.getDescription() + " " + place.getAddress()).toLowerCase();
        
        // 프로필 타입별 필수 조건 확인
        if (!isProfileTypeCompatible(place, profileType)) {
            return false;
        }
        
        // 데이트/로맨틱 조건 - 고급스럽고 분위기 좋은 곳만
        if (conditions.contains("데이트") || conditions.contains("로맨틱") || conditions.contains("분위기")) {
            // 데이트 키워드가 포함된 경우 더욱 엄격한 필터링
            if (conditions.contains("데이트") || conditions.contains("로맨틱")) {
                return isRomanticPlace(placeInfo);
            }
            
            // "분위기"만 있는 경우 중간 정도의 필터링
            if (conditions.contains("분위기")) {
                return isAtmosphericPlace(placeInfo);
            }
        }
        
        // 가족/아이 조건 - 아이들과 함께하기 좋은 곳
        if (conditions.contains("가족") || conditions.contains("아이") || conditions.contains("어린이") || conditions.contains("놀기")) {
            return isFamilyFriendlyPlace(placeInfo);
        }
        
        // 혼자/조용한 곳 조건 - 차분하고 집중할 수 있는 곳
        if (conditions.contains("혼자") || conditions.contains("조용") || conditions.contains("집중")) {
            return isQuietPlace(placeInfo);
        }
        
        // 맛집/음식 조건 - 정말 맛있는 곳
        if (conditions.contains("맛집") || conditions.contains("음식") || conditions.contains("맛")) {
            return isGoodFoodPlace(placeInfo);
        }
        
        // 액티비티/운동 조건 - 활동적인 곳
        if (conditions.contains("액티비티") || conditions.contains("운동") || conditions.contains("스포츠")) {
            return isActivePlace(placeInfo);
        }
        
        // 새로운 조건들
        if (conditions.contains("저렴") || conditions.contains("가성비") || conditions.contains("싸게")) {
            return isBudgetFriendlyPlace(placeInfo);
        }
        
        if (conditions.contains("고급") || conditions.contains("럭셔리") || conditions.contains("프리미엄")) {
            return isLuxuryPlace(placeInfo);
        }
        
        if (conditions.contains("야외") || conditions.contains("실외") || conditions.contains("바깥")) {
            return isOutdoorPlace(placeInfo);
        }
        
        if (conditions.contains("실내") || conditions.contains("에어컨") || conditions.contains("시원")) {
            return isIndoorPlace(placeInfo);
        }
        
        return false;
    }
    
    /**
     * 프로필 타입과 장소의 호환성 확인
     */
    private boolean isProfileTypeCompatible(RecommendedPlace place, String profileType) {
        String placeInfo = (place.getName() + " " + place.getDescription() + " " + place.getAddress()).toLowerCase();
        
        switch (profileType) {
            case "alone":
                // 혼자에게 부적절한 곳 제외
                return !placeInfo.contains("노래방") && !placeInfo.contains("pc방") && 
                       !placeInfo.contains("볼링") && !placeInfo.contains("당구") &&
                       !placeInfo.contains("술집") && !placeInfo.contains("바") && 
                       !placeInfo.contains("클럽");
                       
            case "couple":
                // 커플에게 부적절한 곳 제외
                return !placeInfo.contains("pc방") && !placeInfo.contains("게임장") &&
                       !placeInfo.contains("노래방") && !placeInfo.contains("볼링");
                       
            case "family":
                // 가족에게 부적절한 곳 제외
                return !placeInfo.contains("술집") && !placeInfo.contains("바") && 
                       !placeInfo.contains("클럽") && !placeInfo.contains("노래방") &&
                       !placeInfo.contains("pc방") && !placeInfo.contains("게임장");
                       
            default:
                return true;
        }
    }
    
    /**
     * 부분적으로 조건에 맞는 장소 확인
     */
    private boolean isPlacePartiallyMatchingConditions(RecommendedPlace place, String conditions, String profileType) {
        String placeInfo = (place.getName() + " " + place.getDescription() + " " + place.getAddress()).toLowerCase();
        
        // 프로필 타입 호환성 확인
        if (!isProfileTypeCompatible(place, profileType)) {
            return false;
        }
        
        // 조건 키워드 중 일부라도 포함되면 부분 매칭
        String[] conditionWords = conditions.split("[\\s,]+");
        int matchCount = 0;
        
        for (String word : conditionWords) {
            if (word.length() > 1 && placeInfo.contains(word)) {
                matchCount++;
            }
        }
        
        // 30% 이상의 단어가 매칭되면 부분 매칭으로 판단
        return (double) matchCount / conditionWords.length >= 0.3;
    }
    
    /**
     * 부분 매칭 점수 계산
     */
    private double calculatePartialMatchScore(RecommendedPlace place, String conditions, String profileType) {
        String placeInfo = (place.getName() + " " + place.getDescription() + " " + place.getAddress()).toLowerCase();
        double score = 0.0;
        
        // 조건 키워드 매칭 점수
        String[] conditionWords = conditions.split("[\\s,]+");
        int matchCount = 0;
        
        for (String word : conditionWords) {
            if (word.length() > 1 && placeInfo.contains(word)) {
                matchCount++;
            }
        }
        
        score += (double) matchCount / conditionWords.length * 30.0; // 최대 30점
        
        // 거리 점수
        if (place.getDistanceFromVenue() <= 0.5) {
            score += 20.0;
        } else if (place.getDistanceFromVenue() <= 1.0) {
            score += 15.0;
        } else if (place.getDistanceFromVenue() <= 1.5) {
            score += 10.0;
        }
        
        return score;
    }
    
    /**
     * 최종 점수 계산
     */
    private double calculateFinalScore(RecommendedPlace place, String conditions, String profileType, List<Integer> aiRankedIndices) {
        double score = 0.0;
        
        // 1순위: 완벽한 조건 만족 (매우 중요!)
        if (isPlaceMatchingConditions(place, conditions, profileType)) {
            score += 100.0; // 완벽 매칭 시 100점
            
            // 상세 조건 점수 추가
            double detailedScore = calculateDetailedConditionScore(place, conditions);
            score += detailedScore;
        }
        
        // 2순위: 거리 점수
        if (place.getDistanceFromVenue() <= 0.5) {
            score += 40.0;
        } else if (place.getDistanceFromVenue() <= 1.0) {
            score += 30.0;
        } else if (place.getDistanceFromVenue() <= 1.5) {
            score += 20.0;
        } else if (place.getDistanceFromVenue() <= 2.0) {
            score += 10.0;
        }
        
        // 3순위: AI 순위 점수
        int aiRank = aiRankedIndices.indexOf(place.getId() - 1);
        if (aiRank >= 0) {
            score += (10.0 - aiRank) * 1.0;
        }
        
        // 4순위: 카테고리별 기본 점수
        switch (place.getCategory()) {
            case ACTIVITY -> score += 5.0;
            case DINING -> score += 5.0;
            case CAFE -> score += 5.0;
        }
        
        return score;
    }
    
    /**
     * 일반적인 키워드 매칭 - 더욱 정교한 로직
     */
    private boolean isGeneralMatching(String placeInfo, String conditions) {
        // 조건을 단어별로 분리하고 의미있는 단어만 필터링
        String[] conditionWords = conditions.split("[\\s,]+");
        List<String> meaningfulWords = new ArrayList<>();
        
        for (String word : conditionWords) {
            String trimmedWord = word.trim();
            // 의미있는 단어만 선택 (2글자 이상, 특수문자 제외)
            if (trimmedWord.length() > 1 && !trimmedWord.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
                meaningfulWords.add(trimmedWord.toLowerCase());
            }
        }
        
        // 각 조건 단어가 장소 정보에 포함되는지 확인
        int matchCount = 0;
        int totalWords = meaningfulWords.size();
        
        for (String word : meaningfulWords) {
            if (placeInfo.contains(word)) {
                matchCount++;
            }
        }
        
        // 50% 이상의 단어가 매칭되면 조건 만족으로 판단
        if (totalWords > 0) {
            double matchRatio = (double) matchCount / totalWords;
            return matchRatio >= 0.5;
        }
        
        return false;
    }
    
    /**
     * 조건별 추천 설명 생성 - 사용자에게 왜 이 장소를 추천했는지 설명
     */
    private String generateRecommendationReason(RecommendedPlace place, String conditions) {
        StringBuilder reason = new StringBuilder();
        
        if (conditions.contains("혼자") || conditions.contains("조용")) {
            reason.append("혼자서 조용하게 시간을 보내기 좋은 ");
        } else if (conditions.contains("커플") || conditions.contains("데이트")) {
            reason.append("커플 데이트에 분위기 좋은 ");
        } else if (conditions.contains("가족") || conditions.contains("아이")) {
            reason.append("가족과 함께하기 좋은 ");
        }
        
        if (conditions.contains("맛집") || conditions.contains("음식")) {
            reason.append("맛있는 음식을 즐길 수 있는 ");
        } else if (conditions.contains("카페")) {
            reason.append("편안하게 휴식할 수 있는 ");
        } else if (conditions.contains("액티비티") || conditions.contains("운동")) {
            reason.append("활동적으로 즐길 수 있는 ");
        }
        
        reason.append("장소입니다.");
        
        // 거리 정보 추가
        if (place.getDistanceFromVenue() <= 0.5) {
            reason.append(" (행사장에서 도보 5분 이내)");
        } else if (place.getDistanceFromVenue() <= 1.0) {
            reason.append(" (행사장에서 도보 10분 이내)");
        } else if (place.getDistanceFromVenue() <= 1.5) {
            reason.append(" (행사장에서 도보 15분 이내)");
        }
        
        return reason.toString();
    }
    
    /**
     * 조건별 추천 설명 생성 - 사용자에게 왜 이 장소를 추천했는지 설명
     */
    private String generateDetailedRecommendationReason(RecommendedPlace place, String conditions, String profileType) {
        StringBuilder reason = new StringBuilder();
        
        if (profileType.equals("alone")) {
            if (conditions.contains("혼자") || conditions.contains("조용")) {
                reason.append("혼자서 조용하게 시간을 보내기 좋은 ");
            } else if (conditions.contains("커플") || conditions.contains("데이트")) {
                reason.append("커플 데이트에 분위기 좋은 ");
            } else if (conditions.contains("가족") || conditions.contains("아이")) {
                reason.append("가족과 함께하기 좋은 ");
            }
        } else if (profileType.equals("couple")) {
            if (conditions.contains("커플") || conditions.contains("데이트")) {
                reason.append("커플 데이트에 분위기 좋은 ");
            } else if (conditions.contains("가족") || conditions.contains("아이")) {
                reason.append("가족과 함께하기 좋은 ");
            }
        } else if (profileType.equals("family")) {
            if (conditions.contains("가족") || conditions.contains("아이")) {
                reason.append("가족과 함께하기 좋은 ");
            }
        }
        
        if (conditions.contains("맛집") || conditions.contains("음식")) {
            reason.append("맛있는 음식을 즐길 수 있는 ");
        } else if (conditions.contains("카페")) {
            reason.append("편안하게 휴식할 수 있는 ");
        } else if (conditions.contains("액티비티") || conditions.contains("운동")) {
            reason.append("활동적으로 즐길 수 있는 ");
        }
        
        reason.append("장소입니다.");
        
        // 거리 정보 추가
        if (place.getDistanceFromVenue() <= 0.5) {
            reason.append(" (행사장에서 도보 5분 이내)");
        } else if (place.getDistanceFromVenue() <= 1.0) {
            reason.append(" (행사장에서 도보 10분 이내)");
        } else if (place.getDistanceFromVenue() <= 1.5) {
            reason.append(" (행사장에서 도보 15분 이내)");
        }
        
        return reason.toString();
    }
    
    /**
     * 카테고리별 기본 추천
     */
    private List<RecommendedPlace> getCategoryBasedRecommendations(List<RecommendedPlace> places) {
        // 카테고리별로 그룹화
        Map<RecommendedPlace.PlaceCategory, List<RecommendedPlace>> grouped = places.stream()
                .collect(Collectors.groupingBy(RecommendedPlace::getCategory));
        
        List<RecommendedPlace> result = new ArrayList<>();
        
        // 엑티비티 2개
        if (grouped.containsKey(RecommendedPlace.PlaceCategory.ACTIVITY)) {
            result.addAll(grouped.get(RecommendedPlace.PlaceCategory.ACTIVITY).stream()
                    .limit(2)
                    .collect(Collectors.toList()));
        }
        
        // 식당 2개
        if (grouped.containsKey(RecommendedPlace.PlaceCategory.DINING)) {
            result.addAll(grouped.get(RecommendedPlace.PlaceCategory.DINING).stream()
                    .limit(2)
                    .collect(Collectors.toList()));
        }
        
        // 카페 1개
        if (grouped.containsKey(RecommendedPlace.PlaceCategory.CAFE)) {
            result.addAll(grouped.get(RecommendedPlace.PlaceCategory.CAFE).stream()
                    .limit(1)
                    .collect(Collectors.toList()));
        }
        
        return result;
    }

    // 기존 헬퍼 메서드들
    private String getProfileTypeKorean(ProfileType profileType) {
        return switch (profileType) {
            case ALONE -> "혼자";
            case COUPLE -> "커플";
            case FAMILY -> "가족 단위";
        };
    }

    private String getTransportationKorean(PlaceRecommendationRequest.TransportationMethod method) {
        if (method == null) {
            return "미정";
        }
        
        return switch (method) {
            case WALK -> "도보";
            case CAR -> "자동차";
            case BUS -> "버스";
            case SUBWAY -> "지하철";
        };
    }

    // Hugging Face API DTO
    public static class HuggingFaceRequest {
        public String inputs;
        
        public HuggingFaceRequest(String inputs) {
            this.inputs = inputs;
        }
    }

    public static class HuggingFaceResponse {
        public HuggingFaceGeneratedText[] generated_text;
    }

    public static class HuggingFaceGeneratedText {
        public String generated_text;
    }

    // 조건별 장소 필터링 메서드들
    /**
     * 로맨틱한 장소인지 판단 (더 정확하게)
     */
    private boolean isRomanticPlace(String placeInfo) {
        if (placeInfo == null) return false;
        
        String info = placeInfo.toLowerCase();
        
        // 로맨틱한 키워드 (포함)
        String[] romanticKeywords = {
            "카페", "커피", "와인", "바", "이자카야", "양식", "이탈리안", "프렌치", "스테이크",
            "갤러리", "전시", "문화", "공원", "강변", "해변", "전망", "루프탑", "테라스",
            "분위기", "로맨틱", "데이트", "커플", "연인", "사랑", "아름다운", "예쁜"
        };
        
        // 부적절한 키워드 (제외)
        String[] inappropriateKeywords = {
            "짬뽕", "중국요리", "흑염소", "곰탕", "육류", "고기", "패스트푸드", "맥도날드",
            "게임", "만화", "보드", "놀이", "레저", "운동", "체육", "산", "등산"
        };
        
        // 부적절한 키워드가 있으면 false
        for (String keyword : inappropriateKeywords) {
            if (info.contains(keyword)) {
                return false;
            }
        }
        
        // 로맨틱한 키워드가 있으면 true
        for (String keyword : romanticKeywords) {
            if (info.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 분위기 좋은 음식점인지 판단
     */
    private boolean isAtmosphericRestaurant(String placeName, String description) {
        String info = (placeName + " " + description).toLowerCase();
        
        String[] atmosphericKeywords = {
            "양식", "이탈리안", "프렌치", "스테이크", "와인", "바", "이자카야", "일식",
            "분위기", "고급", "프리미엄", "특별", "유명", "맛집", "레스토랑"
        };
        
        for (String keyword : atmosphericKeywords) {
            if (info.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 데이트하기 적합한 장소인지 판단
     */
    private boolean isDateFriendlyPlace(String placeName, String description) {
        String info = (placeName + " " + description).toLowerCase();
        
        String[] dateFriendlyKeywords = {
            "카페", "커피", "문화", "관광", "전시", "갤러리", "공원", "거리", "쇼핑",
            "영화", "극장", "공연", "음악", "예술", "체험", "관람"
        };
        
        for (String keyword : dateFriendlyKeywords) {
            if (info.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 데이트에 부적절한 장소인지 판단
     */
    private boolean isInappropriateForDate(String placeName, String description) {
        String info = (placeName + " " + description).toLowerCase();
        
        String[] inappropriateKeywords = {
            "짬뽕", "중국요리", "흑염소", "곰탕", "육류", "고기", "패스트푸드", "맥도날드",
            "게임", "만화", "보드", "놀이", "레저", "운동", "체육", "산", "등산", "클럽"
        };
        
        for (String keyword : inappropriateKeywords) {
            if (info.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 분위기 좋은 장소인지 판단
     */
    private boolean isAtmosphericPlace(String placeInfo) {
        if (placeInfo == null) return false;
        
        String info = placeInfo.toLowerCase();
        
        String[] atmosphericKeywords = {
            "분위기", "고급", "프리미엄", "특별", "유명", "아름다운", "예쁜", "로맨틱",
            "전망", "루프탑", "테라스", "갤러리", "전시", "문화", "예술"
        };
        
        for (String keyword : atmosphericKeywords) {
            if (info.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 이색체험 장소인지 판단
     */
    private boolean isUniqueExperience(String placeInfo) {
        if (placeInfo == null) return false;
        
        String info = placeInfo.toLowerCase();
        
        String[] uniqueKeywords = {
            "이색", "특별", "유일", "독특", "신기", "새로운", "체험", "관람", "전시",
            "문화", "예술", "공연", "음악", "영화", "극장", "갤러리", "박물관"
        };
        
        for (String keyword : uniqueKeywords) {
            if (info.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }

    private boolean isFamilyFriendlyPlace(String placeInfo) {
        return placeInfo.contains("공원") || placeInfo.contains("놀이터") || placeInfo.contains("문화") ||
               placeInfo.contains("체육") || placeInfo.contains("스포츠") || placeInfo.contains("수영장") ||
               placeInfo.contains("테니스") || placeInfo.contains("골프") || placeInfo.contains("볼링") ||
               placeInfo.contains("아이스링크") || placeInfo.contains("놀이공원") || placeInfo.contains("동물원") ||
               placeInfo.contains("식당") || placeInfo.contains("레스토랑") || placeInfo.contains("카페") ||
               placeInfo.contains("아이스크림") || placeInfo.contains("베이커리");
    }

    private boolean isQuietPlace(String placeInfo) {
        return placeInfo.contains("카페") || placeInfo.contains("도서관") || placeInfo.contains("공원") ||
               placeInfo.contains("문화") || placeInfo.contains("예술") || placeInfo.contains("갤러리") ||
               placeInfo.contains("박물관") || placeInfo.contains("전시") || placeInfo.contains("공원") ||
               placeInfo.contains("산책") || placeInfo.contains("휴식") || placeInfo.contains("독서");
    }

    private boolean isGoodFoodPlace(String placeInfo) {
        return placeInfo.contains("식당") || placeInfo.contains("레스토랑") || placeInfo.contains("다이닝") ||
               placeInfo.contains("스테이크") || placeInfo.contains("이탈리안") || placeInfo.contains("프렌치") ||
               placeInfo.contains("일식") || placeInfo.contains("중식") || placeInfo.contains("한식") ||
               placeInfo.contains("해산물") || placeInfo.contains("고기") || placeInfo.contains("파스타") ||
               placeInfo.contains("피자") || placeInfo.contains("샌드위치") || placeInfo.contains("디저트");
    }

    private boolean isActivePlace(String placeInfo) {
        return placeInfo.contains("체육") || placeInfo.contains("스포츠") || placeInfo.contains("운동") ||
               placeInfo.contains("공원") || placeInfo.contains("수영장") || placeInfo.contains("테니스") ||
               placeInfo.contains("골프") || placeInfo.contains("볼링") || placeInfo.contains("아이스링크") ||
               placeInfo.contains("클라이밍") || placeInfo.contains("등산") || placeInfo.contains("자전거") ||
               placeInfo.contains("농구") || placeInfo.contains("축구") || placeInfo.contains("야구");
    }

    private boolean isBudgetFriendlyPlace(String placeInfo) {
        return placeInfo.contains("맥도날드") || placeInfo.contains("버거킹") || placeInfo.contains("롯데리아") ||
               placeInfo.contains("kfc") || placeInfo.contains("서브웨이") || placeInfo.contains("도미노") ||
               placeInfo.contains("피자헛") || placeInfo.contains("교촌") || placeInfo.contains("bbq") ||
               placeInfo.contains("분식") || placeInfo.contains("국밥") || placeInfo.contains("칼국수") ||
               placeInfo.contains("김밥") || placeInfo.contains("도시락") || placeInfo.contains("떡볶이");
    }

    private boolean isLuxuryPlace(String placeInfo) {
        return placeInfo.contains("미쉐린") || placeInfo.contains("스테이크") || placeInfo.contains("와인") ||
               placeInfo.contains("고급") || placeInfo.contains("프리미엄") || placeInfo.contains("럭셔리") ||
               placeInfo.contains("호텔") || placeInfo.contains("리조트") || placeInfo.contains("스파");
    }

    private boolean isOutdoorPlace(String placeInfo) {
        return placeInfo.contains("공원") || placeInfo.contains("산책") || placeInfo.contains("자연") ||
               placeInfo.contains("테라스") || placeInfo.contains("루프탑") || placeInfo.contains("정원") ||
               placeInfo.contains("산") || placeInfo.contains("바다") || placeInfo.contains("강");
    }

    private boolean isIndoorPlace(String placeInfo) {
        return placeInfo.contains("쇼핑몰") || placeInfo.contains("백화점") || placeInfo.contains("영화관") ||
               placeInfo.contains("박물관") || placeInfo.contains("갤러리") || placeInfo.contains("도서관") ||
               placeInfo.contains("카페") || placeInfo.contains("레스토랑") || placeInfo.contains("게임장");
    }

    /**
     * 더 관대한 조건으로 AI 추천 (카테고리별 최소 장소 수 보장용)
     */
    public List<RecommendedPlace> getAIRecommendationsWithRelaxedConditions(
            List<RecommendedPlace> places, 
            PlaceRecommendationRequest request, 
            User user, 
            RecommendedPlace.PlaceCategory targetCategory) {
        
        log.info("관대한 조건으로 AI 추천 시작: 카테고리={}, 장소수={}", targetCategory, places.size());
        
        List<RecommendedPlace> scoredPlaces = new ArrayList<>();
        
        for (RecommendedPlace place : places) {
            try {
                // 더 관대한 점수 계산
                double relaxedScore = calculateRelaxedConditionScore(place, request, user, targetCategory);
                
                if (relaxedScore >= 50.0) { // 70점 -> 50점으로 낮춤
                    place.setAiReason(generateRelaxedAiReason(place, request, user, targetCategory));
                    scoredPlaces.add(place);
                }
                
            } catch (Exception e) {
                log.warn("장소 {} 점수 계산 실패: {}", place.getName(), e.getMessage());
            }
        }
        
        // 점수순 정렬 (점수는 별도로 저장하지 않고 aiReason에 포함)
        scoredPlaces.sort((a, b) -> {
            double scoreA = calculateRelaxedConditionScore(a, request, user, targetCategory);
            double scoreB = calculateRelaxedConditionScore(b, request, user, targetCategory);
            return Double.compare(scoreB, scoreA);
        });
        
        log.info("관대한 조건 AI 추천 완료: {}개 장소", scoredPlaces.size());
        return scoredPlaces;
    }
    
    /**
     * 더 관대한 조건 점수 계산
     */
    private double calculateRelaxedConditionScore(RecommendedPlace place, 
                                                PlaceRecommendationRequest request, 
                                                User user, 
                                                RecommendedPlace.PlaceCategory targetCategory) {
        
        double score = 50.0; // 기본 점수 50점
        
        // 카테고리 매칭 보너스
        if (place.getCategory() == targetCategory) {
            score += 20.0;
        }
        
        // 프로필 타입 호환성 (더 관대하게)
        score += calculateRelaxedProfileCompatibility(place, request.getProfileType());
        
        // 커스텀 조건 매칭 (더 관대하게)
        score += calculateRelaxedCustomConditionScore(place, request.getCustomConditions());
        
        // 거리 보너스
        if (place.getDistanceFromVenue() <= 2.0) {
            score += 10.0;
        }
        
        return Math.min(100.0, score);
    }
    
    /**
     * 더 관대한 프로필 호환성 점수
     */
    private double calculateRelaxedProfileCompatibility(RecommendedPlace place, 
                                                      ProfileType profileType) {
        
        double score = 0.0;
        
        switch (profileType) {
            case COUPLE:
                // 커플에 적합한 장소 키워드 (더 넓게)
                if (isRomanticPlace(place.getName()) || 
                    isRomanticPlace(place.getDescription()) ||
                    place.getName().contains("카페") ||
                    place.getName().contains("레스토랑") ||
                    place.getName().contains("문화") ||
                    place.getName().contains("관광")) {
                    score += 15.0;
                }
                break;
            case FAMILY:
                // 가족에 적합한 장소 키워드 (더 넓게)
                if (place.getName().contains("공원") ||
                    place.getName().contains("문화") ||
                    place.getName().contains("관광") ||
                    place.getName().contains("레저") ||
                    place.getName().contains("놀이")) {
                    score += 15.0;
                }
                break;
            case ALONE:
                // 혼자 가기 좋은 장소 키워드 (더 넓게)
                if (place.getName().contains("산") ||
                    place.getName().contains("길") ||
                    place.getName().contains("문화") ||
                    place.getName().contains("카페") ||
                    place.getName().contains("독서")) {
                    score += 15.0;
                }
                break;
        }
        
        return score;
    }
    
    /**
     * 더 관대한 커스텀 조건 점수
     */
    private double calculateRelaxedCustomConditionScore(RecommendedPlace place, String customConditions) {
        double score = 0.0;
        String conditions = customConditions.toLowerCase();
        String placeName = place.getName().toLowerCase();
        String description = place.getDescription().toLowerCase();
        
        // 데이트 관련 키워드 (더 정확하게)
        if (conditions.contains("데이트") || conditions.contains("커플") || conditions.contains("로맨틱")) {
            // 로맨틱한 장소 우선 (높은 점수)
            if (isRomanticPlace(placeName) || isRomanticPlace(description)) {
                score += 50.0; // 최고 점수
            }
            // 분위기 좋은 음식점 (높은 점수)
            else if (isAtmosphericRestaurant(placeName, description)) {
                score += 40.0;
            }
            // 일반적인 데이트 장소 (중간 점수)
            else if (isDateFriendlyPlace(placeName, description)) {
                score += 30.0;
            }
            // 부적절한 장소 (낮은 점수)
            else if (isInappropriateForDate(placeName, description)) {
                score -= 20.0; // 페널티
            }
        }
        
        // 분위기 관련 키워드 (더 정확하게)
        if (conditions.contains("분위기")) {
            if (isAtmosphericPlace(placeName + " " + description)) {
                score += 35.0;
            } else if (isRomanticPlace(placeName) || isRomanticPlace(description)) {
                score += 30.0;
            }
        }
        
        // 이색체험 관련 키워드
        if (conditions.contains("이색") || conditions.contains("체험")) {
            if (isUniqueExperience(placeName + " " + description)) {
                score += 25.0;
            }
        }
        
        return score;
    }
    
    /**
     * 더 관대한 AI 추천 이유 생성
     */
    private String generateRelaxedAiReason(RecommendedPlace place, 
                                         PlaceRecommendationRequest request, 
                                         User user, 
                                         RecommendedPlace.PlaceCategory targetCategory) {
        
        String profileType = request.getProfileType().toString();
        String customConditions = request.getCustomConditions();
        
        if (place.getCategory() == targetCategory) {
            return String.format("%s에 적합한 %s입니다. %s", profileType, place.getName(), customConditions);
        } else {
            return String.format("%s에서 즐길 수 있는 %s입니다. %s", profileType, place.getName(), customConditions);
        }
    }
}
