-- =====================================================================
-- 5번 스케줄 API · 6번 작업 API 로컬 테스트용 seed 데이터 (재실행 가능)
-- 실행: mysql -h 127.0.0.1 -P 3307 -uhackathon -phackathon ai_hackathon < docs/test-data/seed_schedule.sql
--
-- 사용자: 1 = tester1 (주 테스트 계정), 2 = tester2 (타인 소유 검증용)
-- 5번·6번 공용. 8/19 는 user1 작업 3건(1004,1005,1302) → 작업 2개 추가하면 5개(한도), 6번째는 422
-- 스케줄: 101~104 = tester1, 201 = tester2, 105 = tester1 소프트삭제(목록에 안 보여야 함)
-- =====================================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM schedule_change_logs WHERE schedule_id IN (101,102,103,104,105,201);
DELETE FROM schedule_items       WHERE schedule_id IN (101,102,103,104,105,201);
DELETE FROM schedules            WHERE id IN (101,102,103,104,105,201);
DELETE FROM user_preferences     WHERE user_id IN (1,2);
DELETE FROM users                WHERE id IN (1,2);
DELETE FROM categories           WHERE id IN (1,2);

INSERT INTO users (id, email, password_hash, nickname, timezone, status, created_at, updated_at) VALUES
  (1, 'tester1@example.com', NULL, 'tester1', 'Asia/Seoul', 'ACTIVE', NOW(), NOW()),
  (2, 'tester2@example.com', NULL, 'tester2', 'Asia/Seoul', 'ACTIVE', NOW(), NOW());

INSERT INTO user_preferences (user_id, created_at, updated_at) VALUES (1, NOW(), NOW()), (2, NOW(), NOW());
-- max_daily_tasks 는 기본값 5 (6번 MAX_DAILY_TASKS_EXCEEDED 테스트 기준)

-- 카테고리: 1 = 사용 가능, 2 = 사용 중지(is_active=0 → 작업에 지정하면 400)
INSERT INTO categories (id, name, description, display_order, is_active, created_at, updated_at) VALUES
  (1, '공부', NULL, 1, TRUE,  NOW(), NOW()),
  (2, '폐기된 카테고리', NULL, 2, FALSE, NOW(), NOW());

-- ---------- schedules ----------
INSERT INTO schedules (id, user_id, title, description, status, source, start_date, end_date, current_version, created_at, updated_at, deleted_at) VALUES
  (101, 1, '8월 알고리즘 공부',   '매일 알고리즘 문제 풀기',      'ACTIVE',    'AI',   '2026-08-17', '2026-08-30', 1, '2026-08-16 10:00:00', '2026-08-16 10:00:00', NULL),
  (102, 1, '스프링 강의 완강',    '인프런 스프링 핵심 원리',      'DRAFT',     'AI',   '2026-08-20', '2026-09-05', 1, '2026-08-18 09:00:00', '2026-08-18 09:00:00', NULL),
  (103, 1, '7월 영어 단어',       NULL,                          'COMPLETED', 'USER', '2026-07-01', '2026-07-10', 2, '2026-06-30 08:00:00', '2026-07-10 22:00:00', NULL),
  (104, 1, '운동 루틴',           '주 3회 헬스',                 'ACTIVE',    'AI',   '2026-08-01', '2026-08-31', 1, '2026-07-31 20:00:00', '2026-07-31 20:00:00', NULL),
  (105, 1, '삭제된 스케줄',       '목록에 보이면 안 됨',          'ACTIVE',    'USER', '2026-08-01', '2026-08-31', 1, '2026-08-01 00:00:00', '2026-08-02 00:00:00', '2026-08-02 00:00:00'),
  (201, 2, 'tester2의 스케줄',    '타인 소유 — 403/404 검증용',   'ACTIVE',    'AI',   '2026-08-10', '2026-08-31', 1, '2026-08-10 00:00:00', '2026-08-10 00:00:00', NULL);

-- ---------- schedule_items ----------
-- 101: 8/17~8/30, 오늘(2026-08-19) 포함. 일부 완료, 1건 소프트삭제, 1건 취소(퍼즐 수에서 제외)
-- position: 8/18 은 1002(0)→1003(1), 8/19 는 1005(0)→1004(1) (priority 와 반대 → position 정렬 검증용)
INSERT INTO schedule_items (id, schedule_id, user_id, category_id, parent_item_id, title, description, scheduled_date, workload, priority, status, source, position, completed_at, created_at, updated_at, deleted_at) VALUES
  (1001, 101, 1, NULL, NULL, 'BFS 문제 2개',      NULL, '2026-08-17', 2, 2, 'COMPLETED',   'AI', 0, '2026-08-17 21:00:00', '2026-08-16 10:00:00', '2026-08-17 21:00:00', NULL),
  (1002, 101, 1, NULL, NULL, 'DFS 문제 2개',      NULL, '2026-08-18', 2, 2, 'COMPLETED',   'AI', 0, '2026-08-18 22:00:00', '2026-08-16 10:00:00', '2026-08-18 22:00:00', NULL),
  (1003, 101, 1, NULL, NULL, 'DP 기초 강의',      NULL, '2026-08-18', 1, 3, 'SKIPPED',     'AI', 1, NULL,                  '2026-08-16 10:00:00', '2026-08-18 23:00:00', NULL),
  (1004, 101, 1, NULL, NULL, 'DP 문제 1개',       NULL, '2026-08-19', 3, 1, 'TODO',        'AI', 1, NULL,                  '2026-08-16 10:00:00', '2026-08-16 10:00:00', NULL),
  (1005, 101, 1, NULL, NULL, '그리디 문제 1개',   NULL, '2026-08-19', 1, 3, 'IN_PROGRESS', 'AI', 0, NULL,                  '2026-08-16 10:00:00', '2026-08-19 09:00:00', NULL),
  (1006, 101, 1, NULL, NULL, '백트래킹 문제 1개', NULL, '2026-08-20', 2, 2, 'TODO',        'AI', 0, NULL,                  '2026-08-16 10:00:00', '2026-08-16 10:00:00', NULL),
  (1007, 101, 1, NULL, NULL, '복습',              NULL, '2026-08-22', 1, 4, 'TODO',        'AI', 0, NULL,                  '2026-08-16 10:00:00', '2026-08-16 10:00:00', NULL),
  (1008, 101, 1, NULL, NULL, '삭제된 작업',       NULL, '2026-08-21', 1, 3, 'TODO',        'AI', 0, NULL,                  '2026-08-16 10:00:00', '2026-08-17 00:00:00', '2026-08-17 00:00:00'),
  (1009, 101, 1, NULL, NULL, '취소된 작업',       NULL, '2026-08-30', 1, 5, 'CANCELLED',   'USER', 0, NULL,                '2026-08-16 10:00:00', '2026-08-18 00:00:00', NULL);

-- 102: 8/20~9/5 (기간 변경 충돌 테스트: 9/3 에 작업 존재)
INSERT INTO schedule_items (id, schedule_id, user_id, category_id, parent_item_id, title, description, scheduled_date, workload, priority, status, source, position, completed_at, created_at, updated_at, deleted_at) VALUES
  (1101, 102, 1, NULL, NULL, '1강 듣기', NULL, '2026-08-20', 1, 3, 'TODO', 'AI', 0, NULL, '2026-08-18 09:00:00', '2026-08-18 09:00:00', NULL),
  (1102, 102, 1, NULL, NULL, '2강 듣기', NULL, '2026-08-22', 1, 3, 'TODO', 'AI', 0, NULL, '2026-08-18 09:00:00', '2026-08-18 09:00:00', NULL),
  (1103, 102, 1, NULL, NULL, '3강 듣기', NULL, '2026-09-03', 1, 3, 'TODO', 'AI', 0, NULL, '2026-08-18 09:00:00', '2026-08-18 09:00:00', NULL);

-- 103: 7월 완료 스케줄 (전부 COMPLETED)
INSERT INTO schedule_items (id, schedule_id, user_id, category_id, parent_item_id, title, description, scheduled_date, workload, priority, status, source, position, completed_at, created_at, updated_at, deleted_at) VALUES
  (1201, 103, 1, NULL, NULL, 'Day1 단어 30개', NULL, '2026-07-01', 1, 3, 'COMPLETED', 'USER', 0, '2026-07-01 20:00:00', '2026-06-30 08:00:00', '2026-07-01 20:00:00', NULL),
  (1202, 103, 1, NULL, NULL, 'Day2 단어 30개', NULL, '2026-07-02', 1, 3, 'COMPLETED', 'USER', 0, '2026-07-02 20:00:00', '2026-06-30 08:00:00', '2026-07-02 20:00:00', NULL);

-- 104: 운동 (오늘 포함, 8월 캘린더에 섞여서 나와야 함)
INSERT INTO schedule_items (id, schedule_id, user_id, category_id, parent_item_id, title, description, scheduled_date, workload, priority, status, source, position, completed_at, created_at, updated_at, deleted_at) VALUES
  (1301, 104, 1, NULL, NULL, '하체 운동', NULL, '2026-08-17', 2, 2, 'COMPLETED', 'AI', 0, '2026-08-17 19:00:00', '2026-07-31 20:00:00', '2026-08-17 19:00:00', NULL),
  (1302, 104, 1, NULL, NULL, '상체 운동', NULL, '2026-08-19', 2, 2, 'TODO',      'AI', 0, NULL,                  '2026-07-31 20:00:00', '2026-07-31 20:00:00', NULL),
  (1303, 104, 1, NULL, NULL, '유산소',    NULL, '2026-08-21', 1, 3, 'TODO',      'AI', 0, NULL,                  '2026-07-31 20:00:00', '2026-07-31 20:00:00', NULL);

-- 105: 소프트삭제된 스케줄의 작업 (캘린더에 나오면 안 됨)
INSERT INTO schedule_items (id, schedule_id, user_id, category_id, parent_item_id, title, description, scheduled_date, workload, priority, status, source, position, completed_at, created_at, updated_at, deleted_at) VALUES
  (1401, 105, 1, NULL, NULL, '보이면 안 되는 작업', NULL, '2026-08-19', 1, 3, 'TODO', 'USER', 0, NULL, '2026-08-01 00:00:00', '2026-08-02 00:00:00', '2026-08-02 00:00:00');

-- 201: tester2
INSERT INTO schedule_items (id, schedule_id, user_id, category_id, parent_item_id, title, description, scheduled_date, workload, priority, status, source, position, completed_at, created_at, updated_at, deleted_at) VALUES
  (2001, 201, 2, NULL, NULL, 'tester2 작업', NULL, '2026-08-19', 1, 3, 'TODO', 'AI', 0, NULL, '2026-08-10 00:00:00', '2026-08-10 00:00:00', NULL);

SET FOREIGN_KEY_CHECKS = 1;
