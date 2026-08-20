-- AI Scheduler ERD - ERDCloud import file
-- Dialect: MySQL 8.x
-- ERDCloud: New ERD -> Import -> MySQL -> paste/upload this DDL

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NULL,
  nickname VARCHAR(50) NOT NULL,
  profile_image_id BIGINT NULL,
  timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
  status ENUM('ACTIVE','DORMANT','WITHDRAWN','SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  withdrawn_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  UNIQUE KEY uk_users_nickname (nickname),
  KEY idx_users_status (status)
);

CREATE TABLE user_auth_accounts (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  provider ENUM('LOCAL','GOOGLE','KAKAO','NAVER') NOT NULL,
  provider_user_id VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_auth_provider_user (provider, provider_user_id),
  UNIQUE KEY uk_auth_user_provider (user_id, provider)
);

CREATE TABLE auth_sessions (
  id CHAR(36) NOT NULL,
  user_id BIGINT NOT NULL,
  refresh_token_hash VARCHAR(255) NULL,
  user_agent VARCHAR(500) NULL,
  ip_address VARCHAR(45) NULL,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  last_used_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_sessions_user (user_id),
  KEY idx_sessions_expires (expires_at)
);

CREATE TABLE user_preferences (
  user_id BIGINT NOT NULL,
  max_daily_tasks INT NOT NULL DEFAULT 5 COMMENT 'Maximum tasks AI may assign to one date',
  weekend_schedule_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  ai_reschedule_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  default_puzzle_visibility ENUM('PUBLIC','PRIVATE') NOT NULL DEFAULT 'PUBLIC',
  ranking_participation_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  gallery_nickname_visible BOOLEAN NOT NULL DEFAULT TRUE,
  like_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  ranking_change_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (user_id)
);

CREATE TABLE categories (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500) NULL,
  icon_url VARCHAR(1000) NULL,
  display_order INT NOT NULL DEFAULT 0,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_categories_name (name)
);

CREATE TABLE terms (
  id BIGINT NOT NULL AUTO_INCREMENT,
  term_type ENUM('SERVICE','PRIVACY','MARKETING') NOT NULL,
  version VARCHAR(30) NOT NULL,
  title VARCHAR(200) NOT NULL,
  content LONGTEXT NOT NULL,
  is_required BOOLEAN NOT NULL,
  effective_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_terms_type_version (term_type, version)
);

CREATE TABLE user_term_agreements (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  term_id BIGINT NOT NULL,
  agreed BOOLEAN NOT NULL,
  agreed_at DATETIME NOT NULL,
  withdrawn_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_term_agreement (user_id, term_id)
);

CREATE TABLE schedules (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT NULL,
  status ENUM('DRAFT','ACTIVE','COMPLETED','ARCHIVED') NOT NULL DEFAULT 'DRAFT',
  source ENUM('USER','AI','RESCHEDULE_BATCH') NOT NULL DEFAULT 'USER',
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  current_version INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_schedules_user_status (user_id, status),
  KEY idx_schedules_user_period (user_id, start_date, end_date)
);

CREATE TABLE schedule_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  schedule_id BIGINT NOT NULL,
  category_id BIGINT NULL,
  parent_item_id BIGINT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT NULL,
  scheduled_date DATE NOT NULL COMMENT 'Date on which AI assigned the task',
  workload INT NOT NULL DEFAULT 1 COMMENT 'Relative task weight',
  priority TINYINT NOT NULL DEFAULT 3 COMMENT '1 highest, 5 lowest',
  status ENUM('TODO','IN_PROGRESS','COMPLETED','SKIPPED','CANCELLED') NOT NULL DEFAULT 'TODO',
  source ENUM('USER','AI','RESCHEDULE_BATCH') NOT NULL DEFAULT 'USER',
  position INT NOT NULL DEFAULT 0 COMMENT 'Display order within the same date',
  completed_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_items_schedule_date (schedule_id, scheduled_date),
  KEY idx_items_category_status (category_id, status),
  KEY idx_items_parent (parent_item_id)
);

CREATE TABLE batch_jobs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_type ENUM('DAILY_METRICS','RANKING','AI_RESCHEDULE') NOT NULL,
  target_date DATE NOT NULL,
  status ENUM('PENDING','RUNNING','SUCCEEDED','PARTIALLY_SUCCEEDED','FAILED') NOT NULL DEFAULT 'PENDING',
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  total_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  failure_count INT NOT NULL DEFAULT 0,
  error_message TEXT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_type_date (job_type, target_date),
  KEY idx_batch_status (status)
);

CREATE TABLE schedule_change_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  schedule_id BIGINT NOT NULL,
  schedule_item_id BIGINT NULL,
  actor_user_id BIGINT NULL,
  action ENUM('CREATE','UPDATE','DELETE','RESTORE','RESCHEDULE') NOT NULL,
  source ENUM('USER','AI','RESCHEDULE_BATCH') NOT NULL,
  version INT NOT NULL,
  before_data JSON NULL,
  after_data JSON NULL,
  reason TEXT NULL,
  batch_job_id BIGINT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_change_schedule_version (schedule_id, version),
  KEY idx_change_item (schedule_item_id),
  KEY idx_change_batch (batch_job_id)
);

CREATE TABLE conversations (
  id CHAR(36) NOT NULL,
  user_id BIGINT NOT NULL,
  schedule_id BIGINT NULL,
  title VARCHAR(200) NULL,
  status ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  last_message_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_conversations_user_recent (user_id, last_message_at),
  KEY idx_conversations_schedule (schedule_id)
);

CREATE TABLE conversation_messages (
  id CHAR(36) NOT NULL,
  conversation_id CHAR(36) NOT NULL,
  parent_message_id CHAR(36) NULL COMMENT 'Previous message in the selected conversation branch',
  sequence_no INT NOT NULL COMMENT 'Stable message order within a conversation',
  role ENUM('SYSTEM','USER','ASSISTANT','TOOL') NOT NULL,
  action VARCHAR(30) NULL COMMENT 'template, reject, plan_turn, plan_confirmed',
  content LONGTEXT NULL,
  replaces_message_id CHAR(36) NULL COMMENT 'Original message retained when user edits a message',
  model_name VARCHAR(100) NULL,
  prompt_tokens INT NULL,
  completion_tokens INT NULL,
  created_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_messages_sequence (conversation_id, sequence_no),
  KEY idx_messages_conversation_created (conversation_id, created_at),
  KEY idx_messages_parent (parent_message_id),
  KEY idx_messages_replaces (replaces_message_id)
);

CREATE TABLE ai_generation_jobs (
  id CHAR(36) NOT NULL,
  user_id BIGINT NOT NULL,
  conversation_id CHAR(36) NOT NULL,
  schedule_id BIGINT NULL,
  job_type ENUM('CREATE_SCHEDULE','REVISE_SCHEDULE') NOT NULL,
  status ENUM('PENDING','RUNNING','SUCCEEDED','FAILED') NOT NULL DEFAULT 'PENDING',
  instruction TEXT NULL,
  failure_reason TEXT NULL,
  requested_at DATETIME NOT NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_generation_user_requested (user_id, requested_at),
  KEY idx_generation_conversation (conversation_id),
  KEY idx_generation_schedule (schedule_id),
  KEY idx_generation_status (status)
);

CREATE TABLE images (
  id BIGINT NOT NULL AUTO_INCREMENT,
  uploader_user_id BIGINT NOT NULL,
  category_id BIGINT NULL,
  owner_type ENUM('USER','MESSAGE','SCHEDULE','SCHEDULE_ITEM','PUZZLE') NOT NULL,
  owner_id VARCHAR(100) NOT NULL COMMENT 'Polymorphic owner id; application validates target',
  storage_key VARCHAR(700) NOT NULL COMMENT 'S3-compatible object storage key',
  original_filename VARCHAR(255) NULL,
  content_type VARCHAR(100) NOT NULL,
  byte_size BIGINT NOT NULL,
  width INT NULL,
  height INT NULL,
  checksum VARCHAR(128) NULL,
  created_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_images_storage_key (storage_key),
  KEY idx_images_owner (owner_type, owner_id),
  KEY idx_images_uploader (uploader_user_id),
  KEY idx_images_category (category_id)
);

CREATE TABLE puzzles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  schedule_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  image_id BIGINT NULL,
  title VARCHAR(200) NOT NULL,
  status ENUM('IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'IN_PROGRESS',
  visibility ENUM('PUBLIC','PRIVATE') NOT NULL DEFAULT 'PUBLIC',
  completed_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_puzzle_schedule (schedule_id),
  KEY idx_puzzles_user_status (user_id, status),
  KEY idx_puzzles_gallery (visibility, status, completed_at)
);

CREATE TABLE puzzle_pieces (
  id BIGINT NOT NULL AUTO_INCREMENT,
  puzzle_id BIGINT NOT NULL,
  schedule_item_id BIGINT NOT NULL,
  position INT NOT NULL COMMENT 'Piece position within the puzzle image',
  earned_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_piece_schedule_item (schedule_item_id),
  UNIQUE KEY uk_piece_puzzle_position (puzzle_id, position)
);

CREATE TABLE puzzle_likes (
  puzzle_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (puzzle_id, user_id),
  KEY idx_puzzle_likes_user (user_id)
);

CREATE TABLE user_daily_metrics (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  category_id BIGINT NULL COMMENT 'NULL means overall metrics',
  metric_date DATE NOT NULL,
  planned_item_count INT NOT NULL DEFAULT 0,
  completed_item_count INT NOT NULL DEFAULT 0,
  puzzle_count INT NOT NULL DEFAULT 0,
  achievement_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
  consecutive_days INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_metrics_user_category_date (user_id, category_id, metric_date),
  KEY idx_metrics_date_category (metric_date, category_id)
);

CREATE TABLE ranking_snapshots (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ranking_date DATE NOT NULL,
  ranking_type ENUM('STREAK','COMPLETED_PUZZLES','PUZZLE_PIECES') NOT NULL,
  period_type ENUM('DAILY','WEEKLY','MONTHLY','ALL') NOT NULL DEFAULT 'ALL',
  scope ENUM('OVERALL','CATEGORY') NOT NULL,
  category_id BIGINT NULL,
  user_id BIGINT NOT NULL,
  rank_no INT NOT NULL,
  score DECIMAL(14,2) NOT NULL,
  puzzle_count INT NOT NULL DEFAULT 0,
  active_days INT NOT NULL DEFAULT 0,
  achievement_rate DECIMAL(5,2) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ranking_user (ranking_date, ranking_type, period_type, scope, category_id, user_id),
  KEY idx_ranking_lookup (ranking_date, ranking_type, period_type, scope, category_id, rank_no)
);

CREATE TABLE reschedule_results (
  id BIGINT NOT NULL AUTO_INCREMENT,
  batch_job_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  schedule_id BIGINT NOT NULL,
  achievement_rate DECIMAL(5,2) NULL,
  status ENUM('PENDING','RUNNING','SUCCEEDED','PARTIALLY_SUCCEEDED','FAILED') NOT NULL DEFAULT 'PENDING',
  ai_request_id VARCHAR(100) NULL,
  result_summary TEXT NULL,
  error_message TEXT NULL,
  started_at DATETIME NULL,
  finished_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reschedule_batch_user_schedule (batch_job_id, user_id, schedule_id),
  KEY idx_reschedule_user_created (user_id, created_at)
);

ALTER TABLE user_auth_accounts
  ADD CONSTRAINT fk_auth_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE auth_sessions
  ADD CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE user_preferences
  ADD CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE user_term_agreements
  ADD CONSTRAINT fk_agreement_user FOREIGN KEY (user_id) REFERENCES users (id),
  ADD CONSTRAINT fk_agreement_term FOREIGN KEY (term_id) REFERENCES terms (id);

ALTER TABLE schedules
  ADD CONSTRAINT fk_schedule_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE schedule_items
  ADD CONSTRAINT fk_item_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id),
  ADD CONSTRAINT fk_item_category FOREIGN KEY (category_id) REFERENCES categories (id),
  ADD CONSTRAINT fk_item_parent FOREIGN KEY (parent_item_id) REFERENCES schedule_items (id);

ALTER TABLE schedule_change_logs
  ADD CONSTRAINT fk_change_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id),
  ADD CONSTRAINT fk_change_item FOREIGN KEY (schedule_item_id) REFERENCES schedule_items (id),
  ADD CONSTRAINT fk_change_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
  ADD CONSTRAINT fk_change_batch FOREIGN KEY (batch_job_id) REFERENCES batch_jobs (id);

ALTER TABLE conversations
  ADD CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES users (id),
  ADD CONSTRAINT fk_conversation_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id);

ALTER TABLE conversation_messages
  ADD CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
  ADD CONSTRAINT fk_message_parent FOREIGN KEY (parent_message_id) REFERENCES conversation_messages (id),
  ADD CONSTRAINT fk_message_replaced FOREIGN KEY (replaces_message_id) REFERENCES conversation_messages (id);

ALTER TABLE ai_generation_jobs
  ADD CONSTRAINT fk_generation_user FOREIGN KEY (user_id) REFERENCES users (id),
  ADD CONSTRAINT fk_generation_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
  ADD CONSTRAINT fk_generation_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id);

ALTER TABLE images
  ADD CONSTRAINT fk_image_uploader FOREIGN KEY (uploader_user_id) REFERENCES users (id),
  ADD CONSTRAINT fk_image_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL;

ALTER TABLE users
  ADD CONSTRAINT fk_user_profile_image FOREIGN KEY (profile_image_id) REFERENCES images (id);

ALTER TABLE puzzles
  ADD CONSTRAINT fk_puzzle_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id),
  ADD CONSTRAINT fk_puzzle_user FOREIGN KEY (user_id) REFERENCES users (id),
  ADD CONSTRAINT fk_puzzle_image FOREIGN KEY (image_id) REFERENCES images (id);

ALTER TABLE puzzle_pieces
  ADD CONSTRAINT fk_piece_puzzle FOREIGN KEY (puzzle_id) REFERENCES puzzles (id),
  ADD CONSTRAINT fk_piece_schedule_item FOREIGN KEY (schedule_item_id) REFERENCES schedule_items (id);

ALTER TABLE puzzle_likes
  ADD CONSTRAINT fk_like_puzzle FOREIGN KEY (puzzle_id) REFERENCES puzzles (id),
  ADD CONSTRAINT fk_like_user FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE user_daily_metrics
  ADD CONSTRAINT fk_metric_user FOREIGN KEY (user_id) REFERENCES users (id),
  ADD CONSTRAINT fk_metric_category FOREIGN KEY (category_id) REFERENCES categories (id);

ALTER TABLE ranking_snapshots
  ADD CONSTRAINT fk_ranking_user FOREIGN KEY (user_id) REFERENCES users (id),
  ADD CONSTRAINT fk_ranking_category FOREIGN KEY (category_id) REFERENCES categories (id);

ALTER TABLE reschedule_results
  ADD CONSTRAINT fk_result_batch FOREIGN KEY (batch_job_id) REFERENCES batch_jobs (id),
  ADD CONSTRAINT fk_result_user FOREIGN KEY (user_id) REFERENCES users (id),
  ADD CONSTRAINT fk_result_schedule FOREIGN KEY (schedule_id) REFERENCES schedules (id);
