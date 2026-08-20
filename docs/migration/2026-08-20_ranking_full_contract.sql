-- Full ranking/statistics contract alignment for MySQL 8.x.
-- Safe for both the original ERD schema and the short-lived MVP ranking schema.
-- ranking_snapshots and user_daily_metrics are derived data and are rebuilt by the application.

DELETE FROM ranking_snapshots;
DELETE FROM user_daily_metrics;

DROP PROCEDURE IF EXISTS migrate_ranking_full_contract;
DELIMITER //
CREATE PROCEDURE migrate_ranking_full_contract()
BEGIN
  DECLARE object_count INT DEFAULT 0;

  ALTER TABLE ranking_snapshots
    MODIFY ranking_type ENUM('STREAK','COMPLETED_PUZZLES','PUZZLE_PIECES') NOT NULL,
    MODIFY period_type ENUM('DAILY','WEEKLY','MONTHLY','YEARLY','ALL') NOT NULL DEFAULT 'ALL';

  SELECT COUNT(*) INTO object_count
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'ranking_snapshots'
    AND column_name = 'category_key';
  IF object_count = 0 THEN
    ALTER TABLE ranking_snapshots
      ADD COLUMN category_key BIGINT
        GENERATED ALWAYS AS (COALESCE(category_id, 0)) STORED AFTER category_id;
  END IF;

  SELECT COUNT(*) INTO object_count
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'ranking_snapshots'
    AND index_name = 'uk_ranking_user';
  IF object_count > 0 THEN
    ALTER TABLE ranking_snapshots DROP INDEX uk_ranking_user;
  END IF;
  ALTER TABLE ranking_snapshots
    ADD UNIQUE KEY uk_ranking_user (
      ranking_date, ranking_type, period_type, scope, category_key, user_id
    );

  SELECT COUNT(*) INTO object_count
  FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'ranking_snapshots'
    AND constraint_name = 'chk_ranking_scope_category';
  IF object_count = 0 THEN
    ALTER TABLE ranking_snapshots
      ADD CONSTRAINT chk_ranking_scope_category CHECK (
        (scope = 'OVERALL' AND category_id IS NULL)
        OR (scope = 'CATEGORY' AND category_id IS NOT NULL)
      );
  END IF;

  SELECT COUNT(*) INTO object_count
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_daily_metrics'
    AND column_name = 'category_key';
  IF object_count = 0 THEN
    ALTER TABLE user_daily_metrics
      ADD COLUMN category_key BIGINT
        GENERATED ALWAYS AS (COALESCE(category_id, 0)) STORED AFTER category_id;
  END IF;

  SELECT COUNT(*) INTO object_count
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_daily_metrics'
    AND column_name = 'planned_minutes';
  IF object_count = 0 THEN
    ALTER TABLE user_daily_metrics
      ADD COLUMN planned_minutes INT NOT NULL DEFAULT 0 AFTER completed_item_count;
  END IF;

  SELECT COUNT(*) INTO object_count
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'user_daily_metrics'
    AND column_name = 'completed_minutes';
  IF object_count = 0 THEN
    ALTER TABLE user_daily_metrics
      ADD COLUMN completed_minutes INT NOT NULL DEFAULT 0 AFTER planned_minutes;
  END IF;

  SELECT COUNT(*) INTO object_count
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'user_daily_metrics'
    AND index_name = 'uk_metrics_user_category_date';
  IF object_count > 0 THEN
    ALTER TABLE user_daily_metrics DROP INDEX uk_metrics_user_category_date;
  END IF;
  ALTER TABLE user_daily_metrics
    ADD UNIQUE KEY uk_metrics_user_category_date (user_id, category_key, metric_date);
END//
DELIMITER ;

CALL migrate_ranking_full_contract();
DROP PROCEDURE migrate_ranking_full_contract;
