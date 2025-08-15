package com.example.insert.service;

import com.example.insert.entity.RecommendedPlace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import com.example.insert.repository.RecommendedPlaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KakaoMapService {

    private final RecommendedPlaceRepository recommendedPlaceRepository;
    
    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        log.info("KakaoMapService 초기화 - API 키: {}", kakaoApiKey != null ? kakaoApiKey.substring(0, Math.min(10, kakaoApiKey.length())) + "..." : "NULL");
        
        if (kakaoApiKey == null || kakaoApiKey.trim().isEmpty()) {
            log.error("카카오맵 API 키가 설정되지 않았습니다!");
            throw new IllegalStateException("카카오맵 API 키가 설정되지 않았습니다. application.properties를 확인해주세요.");
        }
        
        this.webClient = WebClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + kakaoApiKey)
                .build();
        
        log.info("WebClient 생성 완료 - Authorization 헤더: KakaoAK {}", 
                kakaoApiKey.substring(0, Math.min(10, kakaoApiKey.length())) + "...");
    }

    /**
     * 장소명으로 좌표 검색 후 주변 장소 검색
     */
    public List<RecommendedPlace> searchNearbyPlacesByVenueName(String venueName, int radius) {
        try {
            // 1단계: 장소명으로 좌표 검색
            VenueCoordinates coordinates = searchVenueCoordinates(venueName);
            if (coordinates == null) {
                log.warn("장소 '{}'의 좌표를 찾을 수 없습니다", venueName);
                return new ArrayList<>();
            }
            
            log.info("장소 '{}' 좌표 검색 완료: ({}, {})", venueName, coordinates.latitude, coordinates.longitude);
            
            // 2단계: 인천 지역인 경우 더 넓은 반경으로 검색
            int searchRadius = radius;
            if (isIncheonArea(coordinates)) {
                searchRadius = Math.max(radius, 3000); // 인천 지역은 최소 3km 반경
                log.info("인천 지역 감지: 검색 반경을 {}m로 확장", searchRadius);
            }
            
            // 3단계: 검색된 좌표로 주변 장소 검색
            return searchNearbyPlaces(coordinates.latitude, coordinates.longitude, searchRadius); // 기본 카테고리 사용
            
        } catch (Exception e) {
            log.error("장소명 '{}'으로 주변 장소 검색 중 오류 발생", venueName, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 인천 지역인지 확인
     */
    private boolean isIncheonArea(VenueCoordinates coordinates) {
        // 인천 지역 대략적인 경계 (위도: 37.3~37.8, 경도: 126.4~126.8)
        return coordinates.latitude >= 37.3 && coordinates.latitude <= 37.8 &&
               coordinates.longitude >= 126.4 && coordinates.longitude <= 126.8;
    }

    /**
     * 장소명으로 좌표 검색 - 다중 전략 사용
     */
    private VenueCoordinates searchVenueCoordinates(String venueName) {
        // 전략 1: 원본 검색어로 정확한 검색
        VenueCoordinates result = searchWithQuery(venueName);
        if (result != null) {
            log.info("원본 검색어 '{}'로 정확한 검색 성공", venueName);
            return result;
        }
        
        // 전략 2: 다양한 검색어 변형으로 시도
        result = searchWithMultipleVariations(venueName);
        if (result != null) {
            return result;
        }
        
        // 전략 3: 하드코딩 좌표 (최후의 수단)
        result = getHardcodedCoordinates(venueName);
        if (result != null) {
            log.info("하드코딩된 좌표 사용: {}", result);
            return result;
        }
        
        log.warn("장소 '{}'의 좌표를 찾을 수 없습니다", venueName);
        return null;
    }
    
    /**
     * 다양한 검색어 변형으로 검색 시도
     */
    private VenueCoordinates searchWithMultipleVariations(String venueName) {
        // 변형 1: "역" 제거
        String query1 = venueName.replaceAll("역$", "").replaceAll("역역$", "역");
        if (!query1.equals(venueName)) {
            log.info("검색어 변형 1 시도: '{}' -> '{}'", venueName, query1);
            VenueCoordinates result = searchWithQuery(query1);
            if (result != null) return result;
        }
        
        // 변형 2: "구", "동" 제거
        String query2 = query1.replaceAll("구$", "").replaceAll("동$", "");
        if (!query2.equals(query1)) {
            log.info("검색어 변형 2 시도: '{}' -> '{}'", query1, query2);
            VenueCoordinates result = searchWithQuery(query2);
            if (result != null) return result;
        }
        
        // 변형 3: "경기장", "체육관", "공연장", "컨벤션센터", "아레나" 등 제거
        String query3 = query2.replaceAll("경기장$", "").replaceAll("체육관$", "").replaceAll("공연장$", "")
                              .replaceAll("컨벤션센터$", "").replaceAll("컨벤션$", "").replaceAll("센터$", "")
                              .replaceAll("아트센터$", "").replaceAll("아트$", "").replaceAll("주경기장$", "")
                              .replaceAll("아레나$", "").replaceAll("공연$", "").replaceAll("콘서트$", "")
                              .replaceAll("축제$", "").replaceAll("이벤트$", "").replaceAll("문화$", "")
                              .replaceAll("예술$", "").replaceAll("스포츠$", "").replaceAll("체육$", "");
        if (!query3.equals(query2)) {
            log.info("검색어 변형 3 시도: '{}' -> '{}'", query2, query3);
            VenueCoordinates result = searchWithQuery(query3);
            if (result != null) return result;
        }
        
        // 변형 4: 공백 제거
        String query4 = query3.replaceAll("\\s+", "");
        if (!query4.equals(query3)) {
            log.info("검색어 변형 4 시도: '{}' -> '{}'", query3, query4);
            VenueCoordinates result = searchWithQuery(query4);
            if (result != null) return result;
        }
        
        // 변형 5: 첫 번째 단어만 사용 (예: "인천문학경기장" -> "인천")
        String[] words = query4.split("[\\s,]+");
        if (words.length > 1) {
            String query5 = words[0];
            log.info("검색어 변형 5 시도: '{}' -> '{}'", query4, query5);
            VenueCoordinates result = searchWithQuery(query5);
            if (result != null) return result;
        }
        
        // 변형 6: 인천 지역 특화 - "인천" + 지역명 조합
        if (query4.contains("인천")) {
            // "인천문학" -> "인천" + "문학"
            String[] parts = query4.split("인천");
            if (parts.length > 1 && parts[1].length() > 0) {
                String query6 = "인천" + parts[1];
                log.info("검색어 변형 6 시도: '{}' -> '{}'", query4, query6);
                VenueCoordinates result = searchWithQuery(query6);
                if (result != null) return result;
            }
        }
        
        log.info("모든 검색어 변형 시도 실패");
        return null;
    }
    
    /**
     * 주어진 검색어로 좌표 검색 - 최적화된 파라미터 사용
     */
    private VenueCoordinates searchWithQuery(String query) {
        try {
            // 검색어 정리 및 인코딩
            String cleanQuery = query.trim();
            if (cleanQuery.length() > 15) {
                cleanQuery = cleanQuery.substring(0, 15);
            }
            cleanQuery = cleanQuery.replaceAll("[^가-힣a-zA-Z0-9\\s]", "");
            cleanQuery = cleanQuery.replaceAll("\\s+", " ");
            cleanQuery = cleanQuery.trim();
            if (cleanQuery.isEmpty()) {
                cleanQuery = "인천";
            }

            // URL 인코딩 - 이중 인코딩 방지
            String encodedQuery = java.net.URLEncoder.encode(cleanQuery, "UTF-8");

            String url = String.format("/v2/local/search/keyword.json?query=%s&size=15&page=1&sort=distance", 
                    encodedQuery);
            
            log.info("카카오맵 API 검색 시도: '{}' -> '{}' -> '{}'", query, cleanQuery, encodedQuery);
            
            Mono<KakaoSearchResponse> response = webClient.get()
                    .uri(url)
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .bodyToMono(KakaoSearchResponse.class);
            
            KakaoSearchResponse result = response.block();
            
            log.info("검색 결과: query='{}', total_count={}, documents.size()={}", 
                    cleanQuery,
                    result != null && result.meta != null ? result.meta.total_count : "null",
                    result != null && result.documents != null ? result.documents.size() : "null");
            
            if (result != null && result.documents != null && !result.documents.isEmpty()) {
                KakaoPlace place = result.documents.get(0);
                log.info("검색어 '{}'로 검색 성공: {}", cleanQuery, place);
                double lat = Double.parseDouble(place.getSafeY());
                double lng = Double.parseDouble(place.getSafeX());
                return new VenueCoordinates(lat, lng);
            } else {
                log.warn("검색어 '{}'로 검색 실패: 결과 없음", cleanQuery);
            }
        } catch (Exception e) {
            log.error("검색어 '{}'로 검색 중 오류 발생", query, e);
        }
                return null;
    }
    
    /**
     * 주요 장소들의 하드코딩된 좌표 반환 - 스마트 매칭
     */
    private VenueCoordinates getHardcodedCoordinates(String query) {
        String normalizedQuery = query.toLowerCase().replaceAll("\\s+", "");
        
        // 주요 장소들의 좌표 데이터베이스 (정확한 매칭)
        if (normalizedQuery.contains("강남") || normalizedQuery.contains("강남역")) {
            return new VenueCoordinates(37.5172, 127.0473); // 강남역
        }
        if (normalizedQuery.contains("홍대") || normalizedQuery.contains("홍대입구")) {
            return new VenueCoordinates(37.5572, 126.9254); // 홍대입구역
        }
        if (normalizedQuery.contains("강북") || normalizedQuery.contains("강북역")) {
            return new VenueCoordinates(37.6396, 127.0257); // 강북역
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("문학")) {
            return new VenueCoordinates(37.4344, 126.6941); // 인천문학경기장
        }
        if (normalizedQuery.contains("잠실") || normalizedQuery.contains("잠실역")) {
            return new VenueCoordinates(37.5139, 127.1006); // 잠실역
        }
        if (normalizedQuery.contains("명동") || normalizedQuery.contains("명동역")) {
            return new VenueCoordinates(37.5609, 126.9855); // 명동역
        }
        if (normalizedQuery.contains("동대문") || normalizedQuery.contains("동대문역")) {
            return new VenueCoordinates(37.5714, 127.0098); // 동대문역
        }
        if (normalizedQuery.contains("신촌") || normalizedQuery.contains("신촌역")) {
            return new VenueCoordinates(37.5551, 126.9368); // 신촌역
        }
        if (normalizedQuery.contains("이태원") || normalizedQuery.contains("이태원역")) {
            return new VenueCoordinates(37.5344, 126.9941); // 이태원역
        }
        if (normalizedQuery.contains("건대") || normalizedQuery.contains("건대입구")) {
            return new VenueCoordinates(37.5407, 127.0828); // 건대입구역
        }
        if (normalizedQuery.contains("신림") || normalizedQuery.contains("신림역")) {
            return new VenueCoordinates(37.4842, 126.9294); // 신림역
        }
        
        // 인천 지역 주요 행사장/경기장/공연장 (우선순위 높음)
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("문학")) {
            return new VenueCoordinates(37.4344, 126.6941); // 인천문학경기장
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("아시아드")) {
            return new VenueCoordinates(37.4344, 126.6941); // 인천아시아드주경기장 (문학경기장과 동일)
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("월드컵")) {
            return new VenueCoordinates(37.4344, 126.6941); // 인천월드컵경기장 (문학경기장과 동일)
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("인스파이어")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천 인스파이어 아레나
        }
        if (normalizedQuery.contains("인스파이어") && normalizedQuery.contains("아레나")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천 인스파이어 아레나
        }
        if (normalizedQuery.contains("송도") && normalizedQuery.contains("컨벤션")) {
            return new VenueCoordinates(37.3925, 126.6399); // 송도컨벤션센터
        }
        if (normalizedQuery.contains("송도") && normalizedQuery.contains("아트")) {
            return new VenueCoordinates(37.3925, 126.6399); // 송도아트센터
        }
        if (normalizedQuery.contains("송도") && normalizedQuery.contains("공연")) {
            return new VenueCoordinates(37.3925, 126.6399); // 송도공연장
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("문화")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천문화예술회관
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("예술")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천문화예술회관
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("공연")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천공연장
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("콘서트")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천콘서트홀
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("축제")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천축제장
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("이벤트")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천이벤트장
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("국제공항")) {
            return new VenueCoordinates(37.4602, 126.4407); // 인천국제공항
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("스포츠")) {
            return new VenueCoordinates(37.4344, 126.6941); // 인천스포츠시설
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("체육")) {
            return new VenueCoordinates(37.4344, 126.6941); // 인천체육시설
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("수영")) {
            return new VenueCoordinates(37.4344, 126.6941); // 인천수영장
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("테니스")) {
            return new VenueCoordinates(37.4344, 126.6941); // 인천테니스장
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("골프")) {
            return new VenueCoordinates(37.4344, 126.6941); // 인천골프장
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("볼링")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천볼링장
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("당구")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천당구장
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("노래방")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천노래방
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("PC방")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천PC방
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("게임")) {
            return new VenueCoordinates(37.4560, 126.6750); // 인천게임장
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("공항")) {
            return new VenueCoordinates(37.4602, 126.4407); // 인천공항
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("항")) {
            return new VenueCoordinates(37.4602, 126.4407); // 인천항
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("대교")) {
            return new VenueCoordinates(37.4602, 126.4407); // 인천대교
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("터미널")) {
            return new VenueCoordinates(37.4602, 126.4407); // 인천여객터미널
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("여객")) {
            return new VenueCoordinates(37.4602, 126.4407); // 인천여객터미널
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("부평")) {
            return new VenueCoordinates(37.4890, 126.7244); // 부평구
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("남동")) {
            return new VenueCoordinates(37.4474, 126.7316); // 남동구
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("연수")) {
            return new VenueCoordinates(37.4100, 126.6788); // 연수구
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("계양")) {
            return new VenueCoordinates(37.5712, 126.7374); // 계양구
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("서구")) {
            return new VenueCoordinates(37.4560, 126.6750); // 서구
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("중구")) {
            return new VenueCoordinates(37.4738, 126.6249); // 중구
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("동구")) {
            return new VenueCoordinates(37.4738, 126.6249); // 동구
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("미추홀")) {
            return new VenueCoordinates(37.4634, 126.6500); // 미추홀구
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("강화")) {
            return new VenueCoordinates(37.7474, 126.4850); // 강화군
        }
        if (normalizedQuery.contains("인천") && normalizedQuery.contains("옹진")) {
            return new VenueCoordinates(37.4460, 126.6340); // 옹진군
        }
        
        // 서울 지역 주요 장소들
        if (normalizedQuery.contains("서울역")) {
            return new VenueCoordinates(37.5547, 126.9706); // 서울역
        }
        if (normalizedQuery.contains("용산") || normalizedQuery.contains("용산역")) {
            return new VenueCoordinates(37.5298, 126.9645); // 용산역
        }
        if (normalizedQuery.contains("영등포") || normalizedQuery.contains("영등포역")) {
            return new VenueCoordinates(37.5155, 126.9076); // 영등포역
        }
        if (normalizedQuery.contains("부천") || normalizedQuery.contains("부천역")) {
            return new VenueCoordinates(37.4840, 126.7827); // 부천역
        }
        
        return null; // 해당하는 장소가 없음
    }
    
    /**
     * 행사장 주변 장소 검색 - 더 많은 카테고리와 장소 검색
     */
    public List<RecommendedPlace> searchNearbyPlaces(double latitude, double longitude, int radius) {
        List<RecommendedPlace> places = new ArrayList<>();
        
        try {
            // 엑티비티 장소 검색 (더 많은 카테고리)
            places.addAll(searchPlacesByCategory(latitude, longitude, radius, "AT4", "엑티비티")); // 관광지
            places.addAll(searchPlacesByCategory(latitude, longitude, radius, "AD5", "엑티비티")); // 숙박
            places.addAll(searchPlacesByCategory(latitude, longitude, radius, "SW8", "엑티비티")); // 스포츠시설
            
            // 식당 장소 검색 (더 많은 카테고리)
            places.addAll(searchPlacesByCategory(latitude, longitude, radius, "FD6", "식당")); // 음식점
            places.addAll(searchPlacesByCategory(latitude, longitude, radius, "CE7", "식당")); // 카페/음료점
            
            // 카페 장소 검색 (더 많은 카테고리)
            places.addAll(searchPlacesByCategory(latitude, longitude, radius, "CE7", "카페")); // 카페/음료점
            places.addAll(searchPlacesByCategory(latitude, longitude, radius, "FD6", "카페")); // 음식점 중 카페류
            
            log.info("카카오맵 API로 {}개 장소 검색 완료", places.size());
            
        } catch (Exception e) {
            log.error("카카오맵 API 검색 중 오류 발생", e);
        }
        
        return places;
    }

    /**
     * 카테고리별 장소 검색
     */
    private List<RecommendedPlace> searchPlacesByCategory(double latitude, double longitude, int radius, String category, String categoryName) {
        List<RecommendedPlace> places = new ArrayList<>();
        
        try {
            // 더 많은 장소 검색 (size를 15로 증가)
            String url = String.format("/v2/local/search/category.json?category_group_code=%s&x=%f&y=%f&radius=%d&size=15", 
                    category, longitude, latitude, radius);
            
            Mono<KakaoSearchResponse> response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(KakaoSearchResponse.class);
            
            KakaoSearchResponse result = response.block();
            
            if (result != null && result.documents != null) {
                for (KakaoPlace place : result.documents) {
                    places.add(convertToRecommendedPlace(place, categoryName));
                }
            }
            
        } catch (Exception e) {
            log.error("카테고리 {} 검색 중 오류 발생: {}", categoryName, e.getMessage());
        }
        
        return places;
    }

    /**
     * 카카오맵 응답을 RecommendedPlace로 변환하고 데이터베이스에 저장
     */
    private RecommendedPlace convertToRecommendedPlace(KakaoPlace place, String category) {
        try {
            // 기존에 저장된 장소가 있는지 확인 (중복 방지)
            RecommendedPlace existingPlace = recommendedPlaceRepository.findByLatitudeAndLongitude(
                Double.parseDouble(place.getSafeY()), 
                Double.parseDouble(place.getSafeX())
            );
            
            if (existingPlace != null) {
                log.debug("기존 장소 발견: {} (ID: {})", existingPlace.getName(), existingPlace.getId());
                return existingPlace;
            }
            
            // 새로운 장소 생성
            RecommendedPlace newPlace = RecommendedPlace.builder()
                    .name(place.place_name != null ? place.place_name : "이름 없음")
                    .description(place.category_name != null ? place.category_name : "카테고리 정보 없음")
                    .category(convertCategory(category))
                    .address(place.address_name != null ? place.address_name : "주소 정보 없음")
                    .rating(4.0) // 기본값, 실제로는 별도 API 필요
                    .priceRange("정보 없음")
                    .openingHours(place.place_url != null ? "상세정보 확인" : "정보 없음")
                    .aiReason("카카오맵 기반 실제 장소")
                    .distanceFromVenue(calculateDistance(place.getSafeDistance()))
                    .latitude(Double.parseDouble(place.getSafeY()))
                    .longitude(Double.parseDouble(place.getSafeX()))
                    .phoneNumber(place.phone != null ? place.phone : "전화번호 없음")
                    .website(place.place_url != null ? place.place_url : "")
                    .build();
            
            // 데이터베이스에 저장
            RecommendedPlace savedPlace = recommendedPlaceRepository.save(newPlace);
            log.debug("새로운 장소 저장 완료: {} (ID: {})", savedPlace.getName(), savedPlace.getId());
            
            return savedPlace;
            
        } catch (Exception e) {
            log.error("장소 저장 중 오류 발생: {}", e.getMessage(), e);
            // 저장 실패 시 임시 객체 반환 (id는 null)
            return RecommendedPlace.builder()
                    .name(place.place_name != null ? place.place_name : "이름 없음")
                    .description(place.category_name != null ? place.category_name : "카테고리 정보 없음")
                    .category(convertCategory(category))
                    .address(place.address_name != null ? place.address_name : "주소 정보 없음")
                    .rating(4.0)
                    .priceRange("정보 없음")
                    .openingHours(place.place_url != null ? "상세정보 확인" : "정보 없음")
                    .aiReason("카카오맵 기반 실제 장소")
                    .distanceFromVenue(calculateDistance(place.getSafeDistance()))
                    .latitude(Double.parseDouble(place.getSafeY()))
                    .longitude(Double.parseDouble(place.getSafeX()))
                    .phoneNumber(place.phone != null ? place.phone : "전화번호 없음")
                    .website(place.place_url != null ? place.place_url : "")
                    .build();
        }
    }

    /**
     * 카테고리 변환
     */
    private RecommendedPlace.PlaceCategory convertCategory(String category) {
        return switch (category) {
            case "엑티비티" -> RecommendedPlace.PlaceCategory.ACTIVITY;
            case "식당" -> RecommendedPlace.PlaceCategory.DINING;
            case "카페" -> RecommendedPlace.PlaceCategory.CAFE;
            default -> RecommendedPlace.PlaceCategory.ACTIVITY;
        };
    }

    /**
     * 거리 계산 (미터 단위)
     */
    private double calculateDistance(String distanceStr) {
        try {
            return Double.parseDouble(distanceStr) / 1000.0; // km 단위로 변환
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 좌표 기반 거리 계산 (Haversine 공식)
     */
    private double calculateDistanceByCoordinates(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 지구의 반지름 (km)
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }

    /**
     * 장소 좌표 DTO
     */
    public static class VenueCoordinates {
        public final double latitude;
        public final double longitude;
        
        public VenueCoordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * 장소명으로 검색하여 상세 정보 반환
     */
    public KakaoMapPlaceInfo searchPlaceByName(String placeName, String venueName) {
        try {
            log.info("카카오맵 장소 검색 시작: {} (기준장소: {})", placeName, venueName);
            
            // 1. 기준 장소의 좌표 조회
            VenueCoordinates venueCoords = searchVenueCoordinates(venueName);
            if (venueCoords == null) {
                log.warn("기준 장소 좌표를 찾을 수 없음: {}", venueName);
                return createFallbackPlaceInfo(placeName, null);
            }
            
            // 2. 검색어 정리 (너무 긴 검색어는 잘라내기)
            String cleanSearchQuery = cleanSearchQuery(placeName);
            log.info("정리된 검색어: {} -> {}", placeName, cleanSearchQuery);
            
            // 3. 장소명으로 검색 - 인코딩 문제 완전 해결
            String encodedQuery = java.net.URLEncoder.encode(cleanSearchQuery, "UTF-8");
            // 이중 인코딩 방지: %25 -> % 로 변환
            encodedQuery = encodedQuery.replace("%25", "%");
            
            String searchUrl = String.format(
                "https://dapi.kakao.com/v2/local/search/keyword.json?query=%s&size=1&page=1&sort=distance",
                encodedQuery
            );
            
            log.info("카카오맵 API 호출: {}", searchUrl);
            
            var response = webClient.get()
                    .uri(searchUrl)
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (response == null) {
                log.warn("카카오맵 API 응답이 null: {}", cleanSearchQuery);
                return createFallbackPlaceInfo(placeName, venueCoords);
            }
            
            // 4. 응답 파싱
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);
            
            if (rootNode.has("documents") && rootNode.get("documents").isArray()) {
                JsonNode documents = rootNode.get("documents");
                if (documents.size() > 0) {
                    JsonNode place = documents.get(0);
                    
                    KakaoMapPlaceInfo placeInfo = new KakaoMapPlaceInfo();
                    placeInfo.name = placeName; // 원본 장소명 사용
                    placeInfo.address = place.has("address_name") ? place.get("address_name").asText() : "주소 정보 없음";
                    placeInfo.longitude = place.has("x") ? Double.parseDouble(place.get("x").asText()) : venueCoords.longitude;
                    placeInfo.latitude = place.has("y") ? place.get("y").asDouble() : venueCoords.latitude;
                    
                    // 거리 계산
                    placeInfo.distanceFromVenue = calculateDistanceByCoordinates(
                        venueCoords.latitude, venueCoords.longitude,
                        placeInfo.latitude, placeInfo.longitude
                    );
                    
                    // 기본값 설정
                    placeInfo.rating = 4.0;
                    placeInfo.reviewCount = 10;
                    placeInfo.phoneNumber = "전화번호 정보 없음";
                    placeInfo.openingHours = "영업시간 정보 없음";
                    
                    log.info("카카오맵 검색 성공: {} -> 주소: {}, 거리: {}km", 
                        placeName, placeInfo.address, placeInfo.distanceFromVenue);
                    
                    return placeInfo;
                }
            }
            
            log.warn("카카오맵 검색 결과 없음: {}", cleanSearchQuery);
            return createFallbackPlaceInfo(placeName, venueCoords);
            
        } catch (Exception e) {
            log.error("카카오맵 장소 검색 중 오류 발생: {}", placeName, e);
            return createFallbackPlaceInfo(placeName, null);
        }
    }
    
    /**
     * 장소명 검색어를 더 안전하게 처리하여 카카오맵 API 호출 시 에러를 방지합니다.
     */
    private String cleanSearchQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }
        String cleanedQuery = query.trim();
        
        // 검색어가 너무 길면 앞부분만 사용
        if (cleanedQuery.length() > 10) {
            // 첫 번째 공백을 기준으로 자르기
            int firstSpaceIndex = cleanedQuery.indexOf(' ');
            if (firstSpaceIndex > 0 && firstSpaceIndex < 10) {
                cleanedQuery = cleanedQuery.substring(0, firstSpaceIndex);
            } else {
                cleanedQuery = cleanedQuery.substring(0, 10);
            }
        }
        
        // 특수문자나 복잡한 조합 제거
        cleanedQuery = cleanedQuery.replaceAll("[^가-힣a-zA-Z0-9\\s]", "");
        
        // 공백이 여러 개면 하나로 통일
        cleanedQuery = cleanedQuery.replaceAll("\\s+", " ");
        
        // 앞뒤 공백 제거
        cleanedQuery = cleanedQuery.trim();
        
        // 빈 문자열이면 기본값
        if (cleanedQuery.isEmpty()) {
            cleanedQuery = "카페";
        }
        
        return cleanedQuery;
    }
    
    /**
     * 카카오맵 API 호출 실패 또는 결과 없음 시 사용하는 폴백 메서드
     */
    private KakaoMapPlaceInfo createFallbackPlaceInfo(String placeName, VenueCoordinates venueCoords) {
        log.warn("카카오맵 API 호출 실패 또는 결과 없음. 폴백 장소 정보 생성: {}", placeName);
        
        KakaoMapPlaceInfo fallbackInfo = new KakaoMapPlaceInfo();
        fallbackInfo.name = placeName;
        fallbackInfo.address = "폴백 주소 - " + placeName;
        
        if (venueCoords != null) {
            fallbackInfo.longitude = venueCoords.longitude;
            fallbackInfo.latitude = venueCoords.latitude;
            fallbackInfo.distanceFromVenue = 0.5; // 기본 거리
        } else {
            // 기본 좌표 (인천 문학경기장 근처)
            fallbackInfo.longitude = 126.6834;
            fallbackInfo.latitude = 37.2891;
            fallbackInfo.distanceFromVenue = 1.0;
        }
        
        fallbackInfo.rating = 4.0; // 기본 평점
        fallbackInfo.reviewCount = 5; // 기본 리뷰 수
        fallbackInfo.phoneNumber = "전화번호 없음";
        fallbackInfo.openingHours = "정보 없음";
        
        return fallbackInfo;
    }

    /**
     * 장소 상세 정보 보강
     */
    private void enhancePlaceInfo(KakaoMapPlaceInfo placeInfo, String placeId) {
        try {
            // 카카오맵 장소 상세 정보 API 호출
            String detailUrl = String.format(
                "https://dapi.kakao.com/v2/local/search/keyword.json?query=%s&size=1&page=1",
                java.net.URLEncoder.encode(placeInfo.name, "UTF-8")
            );
            
            var response = webClient.get()
                    .uri(detailUrl)
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (response != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response);
                
                if (rootNode.has("documents") && rootNode.get("documents").isArray()) {
                    JsonNode documents = rootNode.get("documents");
                    if (documents.size() > 0) {
                        JsonNode place = documents.get(0);
                        
                        // 전화번호
                        if (place.has("phone")) {
                            placeInfo.phoneNumber = place.get("phone").asText();
                        }
                        
                        // 카테고리 정보로 평점 추정
                        String category = place.has("category_name") ? place.get("category_name").asText() : "";
                        placeInfo.rating = estimateRatingFromCategory(category);
                        
                        // 리뷰 수 추정 (카테고리 기반)
                        placeInfo.reviewCount = estimateReviewCountFromCategory(category);
                        
                        // 영업시간 (기본값)
                        placeInfo.openingHours = "상세정보 확인";
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("장소 상세 정보 보강 실패: {}", placeInfo.name, e);
        }
    }

    /**
     * 카테고리 기반 평점 추정
     */
    private double estimateRatingFromCategory(String category) {
        if (category.contains("카페") || category.contains("레스토랑")) {
            return 4.2 + Math.random() * 0.6; // 4.2-4.8
        } else if (category.contains("관광") || category.contains("문화")) {
            return 4.0 + Math.random() * 0.8; // 4.0-4.8
        } else if (category.contains("공원") || category.contains("자연")) {
            return 4.3 + Math.random() * 0.5; // 4.3-4.8
        } else {
            return 4.0 + Math.random() * 0.8; // 4.0-4.8
        }
    }

    /**
     * 카테고리 기반 리뷰 수 추정
     */
    private int estimateReviewCountFromCategory(String category) {
        if (category.contains("카페") || category.contains("레스토랑")) {
            return 50 + (int)(Math.random() * 150); // 50-200
        } else if (category.contains("관광") || category.contains("문화")) {
            return 30 + (int)(Math.random() * 70); // 30-100
        } else if (category.contains("공원") || category.contains("자연")) {
            return 20 + (int)(Math.random() * 50); // 20-70
        } else {
            return 25 + (int)(Math.random() * 75); // 25-100
        }
    }

    /**
     * 카카오맵 장소 정보를 담는 클래스
     */
    public static class KakaoMapPlaceInfo {
        public String name;
        public String address;
        public double longitude;
        public double latitude;
        public double distanceFromVenue;
        public double rating;
        public int reviewCount;
        public String phoneNumber;
        public String openingHours;
        
        // Getter 메서드들
        public String getAddress() { return address; }
        public double getLongitude() { return longitude; }
        public double getLatitude() { return latitude; }
        public double getDistanceFromVenue() { return distanceFromVenue; }
        public double getRating() { return rating; }
        public int getReviewCount() { return reviewCount; }
        public String getPhoneNumber() { return phoneNumber; }
        public String getOpeningHours() { return openingHours; }
    }

    // 카카오맵 API 응답 DTO
    public static class KakaoSearchResponse {
        public List<KakaoPlace> documents;
        public KakaoMeta meta;
        
        @Override
        public String toString() {
            return "KakaoSearchResponse{" +
                    "documents=" + documents +
                    ", meta=" + meta +
                    '}';
        }
    }

    public static class KakaoPlace {
        public String id;
        public String place_name;
        public String category_name;
        public String category_group_code;
        public String phone;
        public String address_name;
        public String road_address_name;
        public String x; // longitude
        public String y; // latitude
        public String place_url;
        public String distance;
        
        // null 체크를 위한 안전한 getter 메서드들
        public String getSafeX() {
            return x != null ? x : "0.0";
        }
        
        public String getSafeY() {
            return y != null ? y : "0.0";
        }
        
        public String getSafeDistance() {
            return distance != null ? distance : "0";
        }
        
        @Override
        public String toString() {
            return "KakaoPlace{" +
                    "id='" + id + '\'' +
                    ", place_name='" + place_name + '\'' +
                    ", category_name='" + category_name + '\'' +
                    ", x='" + x + '\'' +
                    ", y='" + y + '\'' +
                    ", address_name='" + address_name + '\'' +
                    '}';
        }
    }

    public static class KakaoMeta {
        public Object same_name; // String이 아닐 수 있음
        public int pageable_count;
        public int total_count;
        public boolean is_end;
        
        @Override
        public String toString() {
            return "KakaoMeta{" +
                    "same_name=" + same_name +
                    ", pageable_count=" + pageable_count +
                    ", total_count=" + total_count +
                    ", is_end=" + is_end +
                    '}';
        }
    }

    /**
     * 인천 문학경기장 주변의 실제 장소들을 검색하여 추천
     */
    public List<KakaoMapPlaceInfo> searchNearbyPlacesForRecommendation(String venueName, String category, int maxResults) {
        try {
            log.info("=== 카카오맵 API 호출 시작 ===");
            log.info("장소명: {}, 카테고리: {}, 최대결과: {}", venueName, category, maxResults);
            log.info("카카오맵 API 키: {}", kakaoApiKey != null ? kakaoApiKey.substring(0, Math.min(10, kakaoApiKey.length())) + "..." : "NULL");
            
            // 1. 기준 장소의 좌표 조회 (인천 문학경기장)
            log.info("1단계: 기준 장소 좌표 조회 시작");
            VenueCoordinates venueCoords = searchVenueCoordinates(venueName);
            if (venueCoords == null) {
                log.error("❌ 기준 장소 좌표를 찾을 수 없음: {}", venueName);
                log.info("하드코딩된 장소로 대체");
                return getHardcodedNearbyPlaces(category, maxResults);
            }
            log.info("✅ 기준 장소 좌표 조회 성공: 위도={}, 경도={}", venueCoords.latitude, venueCoords.longitude);
            
            // 2. 카테고리별 검색어 생성
            log.info("2단계: 카테고리별 검색어 생성");
            String searchQuery = generateCategorySearchQuery(category);
            log.info("검색어: {} -> {}", category, searchQuery);
            
            // 3. 카카오맵 API로 주변 장소 검색
            log.info("3단계: 카카오맵 API 호출");
            String searchUrl = String.format(
                "https://dapi.kakao.com/v2/local/search/keyword.json?query=%s&x=%s&y=%s&radius=2000&size=%d&page=1&sort=distance",
                java.net.URLEncoder.encode(searchQuery, "UTF-8"),
                venueCoords.longitude,
                venueCoords.latitude,
                maxResults
            );
            
            log.info("🔗 카카오맵 API URL: {}", searchUrl);
            log.info("🔑 Authorization 헤더: KakaoAK {}", kakaoApiKey.substring(0, Math.min(10, kakaoApiKey.length())) + "...");
            
            var response = webClient.get()
                    .uri(searchUrl)
                    .header("Authorization", "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (response == null) {
                log.error("❌ 카카오맵 API 응답이 null: {}", searchQuery);
                log.info("하드코딩된 장소로 대체");
                return getHardcodedNearbyPlaces(category, maxResults);
            }
            
            log.info("✅ 카카오맵 API 응답 수신: {} bytes", response.length());
            log.info("📄 응답 내용 전체: {}", response);
            
            // 4. 응답 파싱 및 실제 장소 정보 구성
            log.info("4단계: 응답 파싱 및 장소 정보 구성");
            List<KakaoMapPlaceInfo> places = parseKakaoMapResponse(response, venueCoords, category);
            
            if (places.isEmpty()) {
                log.warn("⚠️ 카카오맵 검색 결과 없음, 하드코딩된 장소 사용: {}", searchQuery);
                log.info("하드코딩된 장소로 대체");
                return getHardcodedNearbyPlaces(category, maxResults);
            }
            
            log.info("🎉 카카오맵 주변 검색 성공: {}개 장소 발견", places.size());
            for (int i = 0; i < Math.min(3, places.size()); i++) {
                KakaoMapPlaceInfo place = places.get(i);
                log.info("  장소 {}: {} - {} (거리: {}km)", i+1, place.name, place.address, place.distanceFromVenue);
            }
            return places;
            
        } catch (Exception e) {
            log.error("💥 카카오맵 주변 장소 검색 중 오류 발생: {}", category, e);
            log.info("에러 발생으로 하드코딩된 장소로 대체");
            return getHardcodedNearbyPlaces(category, maxResults);
        }
    }

    /**
     * 카테고리별 검색어 생성
     */
    private String generateCategorySearchQuery(String category) {
        switch (category.toLowerCase()) {
            case "dining":
            case "restaurant":
                return "음식점";
            case "cafe":
                return "카페";
            case "activity":
            case "entertainment":
                return "문화시설";
            case "shopping":
                return "쇼핑";
            case "culture":
                return "문화시설";
            default:
                return "음식점";
        }
    }
    
    /**
     * 카카오맵 API 응답 파싱
     */
    private List<KakaoMapPlaceInfo> parseKakaoMapResponse(String response, VenueCoordinates venueCoords, String category) {
        List<KakaoMapPlaceInfo> places = new ArrayList<>();
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response);
            
            if (rootNode.has("documents") && rootNode.get("documents").isArray()) {
                JsonNode documents = rootNode.get("documents");
                
                for (JsonNode place : documents) {
                    KakaoMapPlaceInfo placeInfo = new KakaoMapPlaceInfo();
                    placeInfo.name = place.has("place_name") ? place.get("place_name").asText() : "이름 없음";
                    placeInfo.address = place.has("address_name") ? place.get("address_name").asText() : "주소 정보 없음";
                    placeInfo.longitude = place.has("x") ? Double.parseDouble(place.get("x").asText()) : venueCoords.longitude;
                    placeInfo.latitude = place.has("y") ? place.get("y").asDouble() : venueCoords.latitude;
                    
                    // 거리 계산
                    placeInfo.distanceFromVenue = calculateDistanceByCoordinates(
                        venueCoords.latitude, venueCoords.longitude,
                        placeInfo.latitude, placeInfo.longitude
                    );
                    
                    // 카테고리별 기본 정보 설정
                    setCategorySpecificInfo(placeInfo, category);
                    
                    places.add(placeInfo);
                }
            }
            
        } catch (Exception e) {
            log.error("카카오맵 응답 파싱 중 오류 발생", e);
        }
        
        return places;
    }
    
    /**
     * 카테고리별 장소 정보 설정
     */
    private void setCategorySpecificInfo(KakaoMapPlaceInfo placeInfo, String category) {
        switch (category.toLowerCase()) {
            case "dining":
            case "restaurant":
                placeInfo.rating = 4.2 + (Math.random() * 0.8); // 4.2-5.0
                placeInfo.reviewCount = 50 + (int)(Math.random() * 150); // 50-200
                placeInfo.phoneNumber = "전화번호 정보 없음";
                placeInfo.openingHours = "영업시간 정보 없음";
                break;
            case "cafe":
                placeInfo.rating = 4.0 + (Math.random() * 1.0); // 4.0-5.0
                placeInfo.reviewCount = 30 + (int)(Math.random() * 120); // 30-150
                placeInfo.phoneNumber = "전화번호 정보 없음";
                placeInfo.openingHours = "영업시간 정보 없음";
                break;
            default:
                placeInfo.rating = 4.0;
                placeInfo.reviewCount = 20;
                placeInfo.phoneNumber = "전화번호 정보 없음";
                placeInfo.openingHours = "영업시간 정보 없음";
        }
    }

    /**
     * 하드코딩된 장소 정보를 반환합니다. (API 실패 시 에러 표시용)
     */
    private List<KakaoMapPlaceInfo> getHardcodedNearbyPlaces(String category, int maxResults) {
        log.error("🚨 하드코딩된 장소 사용 - 이는 정상이 아닙니다!");
        log.error("카카오맵 API가 실패했거나 응답을 파싱할 수 없습니다.");
        log.error("카테고리: {}, 요청된 결과 수: {}", category, maxResults);

        List<KakaoMapPlaceInfo> places = new ArrayList<>();
        VenueCoordinates venueCoords = new VenueCoordinates(37.4344, 126.6941); // 인천문학경기장 좌표

        KakaoMapPlaceInfo errorPlace = new KakaoMapPlaceInfo();
        errorPlace.name = "⚠️ 카카오맵 API 오류 - " + category + " 검색 실패";
        errorPlace.address = "API 호출 실패로 인한 임시 정보";
        errorPlace.longitude = venueCoords.longitude;
        errorPlace.latitude = venueCoords.latitude;
        errorPlace.distanceFromVenue = 0.0;
        errorPlace.rating = 0.0;
        errorPlace.reviewCount = 0;
        errorPlace.phoneNumber = "API 오류";
        errorPlace.openingHours = "API 오류";

        places.add(errorPlace);
        log.error("하드코딩된 장소 1개 반환 (에러 표시용)");
        return places;
    }
}
