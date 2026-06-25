BEGIN;

DROP TRIGGER IF EXISTS trg_cases_set_updated_at ON cases;

DROP TABLE IF EXISTS evidence_files;
DROP TABLE IF EXISTS cases;
DROP TABLE IF EXISTS reviews;
DROP TABLE IF EXISTS rule_results;
DROP TABLE IF EXISTS risk_scores;

COMMIT;

