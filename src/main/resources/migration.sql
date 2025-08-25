-- Event 테이블에 external_id 컬럼 추가
-- 기존 테이블이 있는 경우에만 실행

-- H2 데이터베이스용
ALTER TABLE events ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);

-- PostgreSQL용 (H2와 호환)
-- ALTER TABLE events ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);

-- 기존 데이터에 대한 기본값 설정 (필요시)
-- UPDATE events SET external_id = NULL WHERE external_id IS NULL;
