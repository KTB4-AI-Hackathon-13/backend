-- Backfill images for puzzles created before plan confirmation persisted puzzles.image_id.
-- The same active category image may be referenced by multiple puzzles.
-- Idempotent: already assigned puzzles are never changed.

UPDATE puzzles p
JOIN schedules s
  ON s.id = p.schedule_id
 AND s.deleted_at IS NULL
JOIN (
  SELECT i.category_id, MIN(i.id) AS image_id
  FROM images i
  JOIN categories c
    ON c.id = i.category_id
   AND c.is_active = TRUE
  WHERE i.deleted_at IS NULL
    AND i.category_id IS NOT NULL
  GROUP BY i.category_id
) category_images
  ON category_images.category_id = s.category_id
SET p.image_id = category_images.image_id
WHERE p.image_id IS NULL
  AND p.deleted_at IS NULL;

SELECT ROW_COUNT() AS backfilled_puzzle_count;

-- Rows returned here need a category image to be uploaded or a schedule category assigned first.
SELECT p.id AS puzzle_id, p.schedule_id, s.category_id
FROM puzzles p
JOIN schedules s ON s.id = p.schedule_id
WHERE p.image_id IS NULL
  AND p.deleted_at IS NULL
  AND s.deleted_at IS NULL
ORDER BY p.id;
