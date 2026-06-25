-- Mona DRP Schema Verification
-- Usage: psql -h HOST -p PORT -U USER -d DB -f scripts/check_drp_schema.sql

\echo '=== Mona DRP Schema Verification ==='
\echo ''

-- 1. Table count (expect 16)
\echo '--- Tables (expect 16) ---'
SELECT count(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE';

-- 2. Table list
\echo ''
\echo '--- Table List ---'
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- 3. Expected tables check
\echo ''
\echo '--- Expected Tables (16) ---'
SELECT t.expected,
       CASE WHEN c.table_name IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS status
FROM (VALUES
  ('entities'),
  ('asset_groups'),
  ('assets'),
  ('exclusions'),
  ('candidate_discoveries'),
  ('candidates'),
  ('crawl_results'),
  ('page_features'),
  ('candidate_asset_matches'),
  ('detection_signals'),
  ('risk_scores'),
  ('rule_results'),
  ('reviews'),
  ('cases'),
  ('evidence_files'),
  ('blob_storage')
) t(expected)
LEFT JOIN information_schema.tables c
  ON c.table_name = t.expected
  AND c.table_schema = 'public'
  AND c.table_type = 'BASE TABLE'
ORDER BY t.expected;

-- 4. Trigger count (expect 7: updated_at triggers)
\echo ''
\echo '--- Triggers (expect 7) ---'
SELECT count(*) AS trigger_count
FROM information_schema.triggers
WHERE trigger_schema = 'public';

\echo ''
\echo '--- Trigger List ---'
SELECT trigger_name, event_object_table, event_manipulation
FROM information_schema.triggers
WHERE trigger_schema = 'public'
ORDER BY event_object_table, trigger_name;

-- 5. Foreign key count (expect 24)
\echo ''
\echo '--- Foreign Keys (expect 24) ---'
SELECT count(*) AS fk_count
FROM information_schema.table_constraints
WHERE constraint_type = 'FOREIGN KEY'
  AND constraint_schema = 'public';

-- 6. Index count
\echo ''
\echo '--- Index Count ---'
SELECT count(*) AS index_count
FROM pg_indexes
WHERE schemaname = 'public';

-- 7. PGMQ queues
\echo ''
\echo '--- PGMQ Queues (expect 5) ---'
SELECT queue_name, is_partitioned, is_unlogged, created_at
FROM pgmq.list_queues()
ORDER BY queue_name;

-- 8. Expected queues check
\echo ''
\echo '--- Expected Queues ---'
SELECT q.expected,
       CASE WHEN l.queue_name IS NOT NULL THEN 'OK' ELSE 'MISSING' END AS status
FROM (VALUES
  ('candidate_validation_queue'),
  ('crawl_queue'),
  ('feature_extraction_queue'),
  ('risk_scoring_queue'),
  ('similarity_queue')
) q(expected)
LEFT JOIN pgmq.list_queues() l ON l.queue_name = q.expected
ORDER BY q.expected;

\echo ''
\echo '=== Verification complete ==='
