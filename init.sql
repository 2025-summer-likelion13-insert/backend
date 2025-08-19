-- PostgreSQL 초기화 스크립트
CREATE DATABASE IF NOT EXISTS insert_db;
\c insert_db;

-- 사용자 테이블 생성
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    profile_type VARCHAR(20),
    phone_number VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 이벤트 테이블 생성
CREATE TABLE IF NOT EXISTS events (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50),
    duration_minutes INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 추천 장소 테이블 생성
CREATE TABLE IF NOT EXISTS recommended_places (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address TEXT,
    category VARCHAR(50),
    rating DECIMAL(3,2),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 사용자 스케줄 테이블 생성
CREATE TABLE IF NOT EXISTS user_schedules (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    visit_date TIMESTAMP,
    visit_order INTEGER,
    notes TEXT,
    is_visited BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (event_id) REFERENCES events(id),
    FOREIGN KEY (place_id) REFERENCES recommended_places(id)
);

-- 리뷰 테이블 생성
CREATE TABLE IF NOT EXISTS reviews (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    place_id BIGINT NOT NULL,
    rating INTEGER CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (place_id) REFERENCES recommended_places(id)
);

-- 초기 데이터 삽입
INSERT INTO users (email, password, name, profile_type) 
VALUES 
    ('test@example.com', 'password123', '테스트사용자', 'COUPLE'),
    ('user2@example.com', 'password123', '사용자2', 'COUPLE')
ON CONFLICT (email) DO NOTHING;

INSERT INTO events (name, description, category, duration_minutes)
VALUES 
    ('카페 방문', '편안한 카페에서 시간 보내기', 'FOOD', 60),
    ('영화 감상', '최신 영화 감상하기', 'ENTERTAINMENT', 120),
    ('공원 산책', '자연 속에서 힐링하기', 'OUTDOOR', 90)
ON CONFLICT DO NOTHING;

INSERT INTO recommended_places (name, address, category, rating, description)
VALUES 
    ('스타벅스 강남점', '서울 강남구 강남대로 123', 'CAFE', 4.5, '편안한 분위기의 카페'),
    ('CGV 강남점', '서울 강남구 강남대로 456', 'CINEMA', 4.3, '최신 영화 상영관'),
    ('한강공원', '서울 영등포구 여의도로 123', 'PARK', 4.7, '자연 속 힐링 공간')
ON CONFLICT DO NOTHING;
