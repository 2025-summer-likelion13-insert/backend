# Insert Backend Application

## 🚀 빠른 시작

### 1. 개발 환경 (H2 파일 기반 DB)
```bash
# 기본 실행 (H2 파일 기반, 데이터 유지)
./gradlew bootRun
```

### 2. 프로덕션 환경 (PostgreSQL)
```bash
# PostgreSQL 시작
docker-compose up -d

# 프로덕션 프로필로 실행
./gradlew bootRun --args='--spring.profiles.active=prod'
```

## 🗄️ 데이터베이스 설정

### 개발 환경 (H2)
- **URL**: `jdbc:h2:file:./data/insert_dev`
- **설정**: `application-dev.properties`
- **특징**: 파일 기반으로 데이터 유지, 앱 재시작 시에도 데이터 보존

### 프로덕션 환경 (PostgreSQL)
- **URL**: `jdbc:postgresql://localhost:5432/insert_db`
- **설정**: `application-prod.properties`
- **특징**: 안정적인 관계형 데이터베이스, 데이터 영구 보존

## 🔧 환경별 실행 방법

### 개발 모드 (기본)
```bash
./gradlew bootRun
```
- H2 파일 기반 DB 사용
- 데이터 유지됨
- 디버그 로그 활성화

### 프로덕션 모드
```bash
# PostgreSQL 시작
docker-compose up -d

# 프로덕션 프로필로 실행
./gradlew bootRun --args='--spring.profiles.active=prod'
```
- PostgreSQL 사용
- 프로덕션 최적화 설정
- 정보 로그만 출력

## 📊 데이터베이스 접근

### H2 콘솔 (개발 환경)
- **URL**: http://localhost:8080/h2-console
- **JDBC URL**: `jdbc:h2:file:./data/insert_dev`
- **Username**: `sa`
- **Password**: (비어있음)

### PostgreSQL (프로덕션 환경)
```bash
# PostgreSQL 접속
docker exec -it insert_postgres psql -U postgres -d insert_db

# 테이블 확인
\dt

# 데이터 확인
SELECT * FROM users;
```

## 🚨 문제 해결

### 데이터가 사라지는 문제
- **원인**: H2 인메모리 DB 사용 (`create-drop` 모드)
- **해결**: H2 파일 기반 또는 PostgreSQL 사용

### PostgreSQL 연결 실패
```bash
# PostgreSQL 상태 확인
docker ps

# 로그 확인
docker logs insert_postgres

# 재시작
docker-compose restart
```

## 📁 프로젝트 구조
```
backend/
├── src/main/java/com/example/insert/
│   ├── controller/     # REST API 컨트롤러
│   ├── service/        # 비즈니스 로직
│   ├── repository/     # 데이터 접근 계층
│   ├── entity/         # JPA 엔티티
│   └── config/         # 설정 클래스
├── src/main/resources/
│   ├── application.properties      # 기본 설정 (H2 파일 기반)
│   ├── application-dev.properties  # 개발 환경 설정
│   ├── application-prod.properties # 프로덕션 환경 설정
│   └── data.sql                   # 초기 데이터
├── docker-compose.yml  # PostgreSQL Docker 설정
└── init.sql           # PostgreSQL 초기화 스크립트
```

## 🔐 기본 사용자 계정
- **Email**: `test@example.com`
- **Password**: `password123`
- **Name**: `테스트사용자`

- **Email**: `user2@example.com`
- **Password**: `password123`
- **Name**: `사용자2`

## 🔑 API 키 설정 (필수)

### 환경변수 설정
```bash
# Windows PowerShell
$env:KAKAO_API_KEY="your-kakao-api-key"
$env:HUGGINGFACE_API_KEY="your-huggingface-api-key"

# Windows CMD
set KAKAO_API_KEY=your-kakao-api-key
set HUGGINGFACE_API_KEY=your-huggingface-api-key

# Linux/Mac
export KAKAO_API_KEY="your-kakao-api-key"
export HUGGINGFACE_API_KEY="your-huggingface-api-key"
```

### API 키 발급 방법
- **Kakao Maps API**: https://developers.kakao.com/
- **Hugging Face API**: https://huggingface.co/settings/tokens

## 📝 주요 변경사항
1. **H2 인메모리 → 파일 기반**: 데이터 유지
2. **PostgreSQL 지원**: 프로덕션 환경 준비
3. **환경별 설정 분리**: 개발/프로덕션 설정 분리
4. **자동 데이터 초기화**: 앱 시작 시 기본 데이터 생성
