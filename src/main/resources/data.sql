INSERT INTO users (email, password, name, profile_type, created_at, updated_at)
VALUES
    ('test@example.com', 'password123', '테스트사용자', 'COUPLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('user2@example.com', 'password123', '사용자2', 'COUPLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 초기 이벤트 데이터
INSERT INTO events (name, description, event_date, venue_name, venue_address, category, image_url, created_at, updated_at)
VALUES
    ('카페 방문', '편안한 카페에서 시간 보내기', NOW(), '스타벅스 강남점', '서울 강남구 강남대로 123', 'FOOD', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('영화 감상', '최신 영화 감상하기', NOW(), 'CGV 강남점', '서울 강남구 강남대로 456', 'ENTERTAINMENT', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('공원 산책', '자연 속에서 힐링하기', NOW(), '한강공원', '서울 영등포구 여의도로 123', 'OUTDOOR', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO recommended_places (name, address, category, rating, description, created_at, updated_at)
VALUES
    ('스타벅스 강남점', '서울 강남구 강남대로 123', 'CAFE', 4.5, '편안한 분위기의 카페', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('CGV 강남점', '서울 강남구 강남대로 456', 'CINEMA', 4.3, '최신 영화 상영관', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('한강공원', '서울 영등포구 여의도로 123', 'PARK', 4.7, '자연 속 힐링 공간', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
