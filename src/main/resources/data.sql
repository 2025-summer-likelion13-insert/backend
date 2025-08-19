-- 초기 사용자 데이터 (앱 시작 시 자동 실행)
INSERT INTO users (email, password, name, profile_type, created_at, updated_at) 
VALUES 
    ('test@example.com', 'password123', '테스트사용자', 'COUPLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('user2@example.com', 'password123', '사용자2', 'COUPLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

-- 초기 이벤트 데이터
INSERT INTO events (name, description, category, duration_minutes, created_at, updated_at)
VALUES 
    ('카페 방문', '편안한 카페에서 시간 보내기', 'FOOD', 60, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('영화 감상', '최신 영화 감상하기', 'ENTERTAINMENT', 120, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('공원 산책', '자연 속에서 힐링하기', 'OUTDOOR', 90, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- 초기 장소 데이터
INSERT INTO recommended_places (name, address, category, rating, description, created_at, updated_at)
VALUES 
    ('스타벅스 강남점', '서울 강남구 강남대로 123', 'CAFE', 4.5, '편안한 분위기의 카페', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CGV 강남점', '서울 강남구 강남대로 456', 'CINEMA', 4.3, '최신 영화 상영관', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('한강공원', '서울 영등포구 여의도로 123', 'PARK', 4.7, '자연 속 힐링 공간', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
