BEGIN;

DROP TRIGGER IF EXISTS trg_candidates_set_updated_at ON candidates;
DROP TRIGGER IF EXISTS trg_candidate_discoveries_set_updated_at ON candidate_discoveries;

DROP TABLE IF EXISTS candidates;
DROP TABLE IF EXISTS candidate_discoveries;

COMMIT;

