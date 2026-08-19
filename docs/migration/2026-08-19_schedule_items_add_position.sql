-- 2026-08-19 (5·6번 스케줄/작업 API) — schedule_items 에 같은 날짜 안 표시 순서 컬럼 추가
-- ERD md 4.3 에는 있었으나 scheduler_erdcloud_mysql.sql 에 빠져 있던 컬럼. docs/scheduler_erdcloud_mysql.sql 에도 반영됨.
-- 이미 ERD SQL 로 DB 를 만든 사람은 이 문장만 실행하면 된다 (ddl-auto: validate 라 없으면 기동 실패).
ALTER TABLE schedule_items
  ADD COLUMN position INT NOT NULL DEFAULT 0 COMMENT 'Display order within the same date' AFTER source;
