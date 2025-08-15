# InSert - AI 기반 장소 추천 시스템 백엔드

## 프로젝트 개요

InSert는 사용자가 선택한 공연이나 행사를 기반으로, AI가 주변 장소를 추천해주는 스마트한 장소 추천 시스템입니다. 사용자의 프로필, 이동수단, 원하는 조건 등을 고려하여 맞춤형 장소를 제안하고, 선택한 장소를 일정에 담을 수 있습니다.

## 주요 기능

### 1. AI 기반 장소 추천
- 사용자 프로필 (혼자/커플/가족 단위) 기반 추천
- 이동수단 (도보/자동차/버스/지하철) 고려
- 사용자 정의 조건 (최대 50자) 반영
- **카테고리별 추천: 각 카테고리당 정확히 3개씩 장소 추천**
  - 엑티비티: 관광지, 스포츠시설, 숙박 등
  - 식사 장소: 음식점, 카페/음료점 등
  - 카페: 카페/음료점, 분위기 좋은 장소 등
- **중복 없는 다양한 장소 추천**
- **AI가 추천한 이유 상세 설명**

### 2. 장소 상세 정보
- 장소별 상세 설명 및 이미지
- 평점, 가격대, 영업시간 정보
- AI가 추천한 이유 설명
- 행사 장소로부터의 거리 정보

### 3. 사용자 일정 관리
- 추천 장소를 일정에 추가/제거
- 방문 완료 상태 관리
- 이벤트별 일정 조회

## 기술 스택

- **Backend**: Spring Boot 3.2.0
- **Database**: H2 (개발용), PostgreSQL (운영용)
- **ORM**: Spring Data JPA
- **Security**: Spring Security
- **AI Integration**: Hugging Face AI Models
- **Maps API**: Kakao Maps API
- **Build Tool**: Gradle
- **Java Version**: 17

## 프로젝트 구조

```
src/main/java/com/example/insert/
├── config/
│   └── SecurityConfig.java          # 보안 설정
├── controller/
│   ├── PlaceRecommendationController.java  # 장소 추천 API
│   └── UserScheduleController.java          # 사용자 일정 관리 API
├── dto/
│   ├── PlaceRecommendationRequest.java     # 추천 요청 DTO
│   └── PlaceRecommendationResponse.java    # 추천 응답 DTO
├── entity/
│   ├── User.java                           # 사용자 엔티티
│   ├── Event.java                          # 이벤트/공연 엔티티
│   ├── RecommendedPlace.java               # 추천 장소 엔티티
│   └── UserSchedule.java                   # 사용자 일정 엔티티
├── service/
│   └── AIRecommendationService.java        # AI 추천 서비스
└── InsertApplication.java                   # 메인 애플리케이션
```

## 설치 및 실행

### 1. 사전 요구사항
- Java 17 이상
- Gradle 7.0 이상

### 2. 환경 변수 설정
```bash
# Hugging Face API 키 설정
export HUGGINGFACE_API_KEY="your-huggingface-api-key-here"

# Kakao Maps API 키 설정
export KAKAO_API_KEY="your-kakao-api-key-here"

# Hugging Face 모델 URL 설정
export HUGGINGFACE_MODEL_URL="https://api-inference.huggingface.co/models/your-model-name"
```

### 3. 프로젝트 빌드 및 실행
```bash
# 프로젝트 디렉토리로 이동
cd backend

# 의존성 다운로드 및 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun
```

### 4. 접속 확인
- 애플리케이션: http://localhost:8080
- H2 데이터베이스 콘솔: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:testdb`
  - Username: `sa`
  - Password: (비어있음)

## API 엔드포인트

### 장소 추천 API

#### 1. 장소 추천 요청
```http
POST /api/recommendations/places
Content-Type: application/json

{
  "eventId": 1,
  "profileType": "COUPLE",
  "transportationMethods": ["WALK", "SUBWAY"],
  "customConditions": "분위기 좋은 곳으로"
}
```

#### 2. 장소 상세 정보 조회
```http
GET /api/recommendations/places/{placeId}
```

### 사용자 일정 관리 API

#### 1. 장소를 일정에 추가
```http
POST /api/schedules/places
Content-Type: application/json

{
  "userId": 1,
  "eventId": 1,
  "placeId": 1
}
```

#### 2. 사용자 일정 조회
```http
GET /api/schedules/users/{userId}/events/{eventId}
```

#### 3. 일정에서 장소 제거
```http
DELETE /api/schedules/places/{scheduleId}
```

#### 4. 방문 상태 업데이트
```http
PUT /api/schedules/places/{scheduleId}/visit
Content-Type: application/json

{
  "isVisited": true
}
```

## 데이터 모델

### 사용자 (User)
- 기본 정보: 이메일, 비밀번호, 이름, 전화번호
- 프로필 타입: ALONE, COUPLE, FAMILY
- 생성/수정 시간

### 이벤트 (Event)
- 행사 정보: 이름, 설명, 날짜/시간
- 장소 정보: 장소명, 주소, 위도/경도
- 카테고리, 이미지 URL

### 추천 장소 (RecommendedPlace)
- 장소 정보: 이름, 설명, 주소, 위도/경도
- 카테고리: ACTIVITY, DINING, CAFE
- 상세 정보: 평점, 가격대, 영업시간, 전화번호, 웹사이트
- AI 추천 이유, 행사 장소로부터의 거리

### 사용자 일정 (UserSchedule)
- 사용자, 이벤트, 추천 장소 연결
- 방문 날짜, 방문 순서, 메모
- 방문 완료 여부

## AI 추천 로직

1. **프롬프트 생성**: 사용자 프로필, 이동수단, 조건, 이벤트 정보를 종합
2. **OpenAI API 호출**: GPT-3.5 Turbo 모델을 사용하여 맞춤형 추천 생성
3. **응답 파싱**: AI 응답을 구조화된 데이터로 변환
4. **카테고리별 그룹화**: 엑티비티, 식사, 카페로 분류하여 응답 구성

## 개발 환경 설정

### 1. IDE 설정
- IntelliJ IDEA 또는 Eclipse 사용 권장
- Lombok 플러그인 설치 필요

### 2. 데이터베이스 설정
- 개발 환경: H2 인메모리 데이터베이스
- 운영 환경: PostgreSQL 사용 권장

### 3. 로깅 설정
- 로그 레벨: DEBUG (개발), INFO (운영)
- Spring Security 로깅 활성화

## 향후 개선 사항

### 1. AI 통합 강화
- 실제 OpenAI API 클라이언트 구현
- 프롬프트 최적화 및 A/B 테스트
- 추천 품질 향상을 위한 피드백 시스템

### 2. 데이터베이스 연동
- 실제 사용자 인증 시스템 구현
- 이벤트 및 장소 데이터베이스 구축
- 사용자 행동 데이터 수집 및 분석

### 3. 성능 최적화
- 캐싱 시스템 도입
- API 응답 시간 최적화
- 데이터베이스 인덱싱 최적화

### 4. 추가 기능
- 실시간 위치 기반 추천
- 사용자 선호도 학습
- 소셜 기능 (친구와 일정 공유)

## 문제 해결

### 1. 빌드 오류
```bash
# Gradle 캐시 정리
./gradlew clean build
```

### 2. 포트 충돌
```bash
# application.properties에서 포트 변경
server.port=8081
```

### 3. 데이터베이스 연결 오류
- H2 콘솔 접속 확인
- 데이터베이스 설정 검증

## 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

## 기여 방법

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 문의사항

프로젝트에 대한 문의사항이나 버그 리포트는 이슈를 통해 제출해 주세요.
