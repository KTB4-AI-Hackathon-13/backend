-- Align local/development ranking periods with the production DB contract.
-- ranking_snapshots is derived data, so YEARLY rows can be discarded safely.

DELETE FROM ranking_snapshots
WHERE period_type = 'YEARLY';

ALTER TABLE ranking_snapshots
  MODIFY period_type ENUM('DAILY','WEEKLY','MONTHLY','ALL') NOT NULL DEFAULT 'ALL';
