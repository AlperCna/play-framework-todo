BEGIN;

CREATE TABLE risk_scores (
  id               BIGSERIAL PRIMARY KEY,
  candidate_id     BIGINT       NOT NULL,
  crawl_result_id  BIGINT,
  total_score      NUMERIC(5,4) NOT NULL,
  verdict          TEXT         NOT NULL,
  confidence       NUMERIC(5,4),
  reasons          JSONB        NOT NULL DEFAULT '{}'::jsonb,
  llm_summary      TEXT,
  rule_set_version TEXT         NOT NULL,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_risk_scores_candidate
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE RESTRICT,
  CONSTRAINT fk_risk_scores_crawl_result
    FOREIGN KEY (crawl_result_id) REFERENCES crawl_results(id) ON DELETE RESTRICT,
  CONSTRAINT ck_risk_scores_total_score
    CHECK (total_score >= 0 AND total_score <= 1),
  CONSTRAINT ck_risk_scores_confidence
    CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
  CONSTRAINT ck_risk_scores_verdict
    CHECK (verdict IN ('clean', 'suspicious', 'malicious'))
);

CREATE TABLE rule_results (
  id            BIGSERIAL PRIMARY KEY,
  risk_score_id BIGINT       NOT NULL,
  rule_code     TEXT         NOT NULL,
  weight        NUMERIC(5,4) NOT NULL,
  detail        JSONB,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_rule_results_risk_score
    FOREIGN KEY (risk_score_id) REFERENCES risk_scores(id) ON DELETE RESTRICT,
  CONSTRAINT ck_rule_results_weight
    CHECK (weight >= 0 AND weight <= 1)
);

CREATE TABLE reviews (
  id            BIGSERIAL PRIMARY KEY,
  candidate_id  BIGINT      NOT NULL,
  risk_score_id BIGINT      NOT NULL,
  reviewer      TEXT        NOT NULL,
  decision      TEXT        NOT NULL,
  notes         TEXT,
  reviewed_at   TIMESTAMPTZ NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_reviews_candidate
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE RESTRICT,
  CONSTRAINT fk_reviews_risk_score
    FOREIGN KEY (risk_score_id) REFERENCES risk_scores(id) ON DELETE RESTRICT,
  CONSTRAINT ck_reviews_decision
    CHECK (decision IN ('confirmed', 'false_positive', 'needs_more_info'))
);

CREATE TABLE cases (
  id               BIGSERIAL PRIMARY KEY,
  candidate_id     BIGINT      NOT NULL,
  review_id        BIGINT      NOT NULL,
  status           TEXT        NOT NULL DEFAULT 'open',
  priority         TEXT,
  takedown_sent_at TIMESTAMPTZ,
  notes            TEXT,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_cases_candidate
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE RESTRICT,
  CONSTRAINT fk_cases_review
    FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE RESTRICT,
  CONSTRAINT ck_cases_status
    CHECK (status IN ('open', 'takedown_requested', 'closed', 'false_positive')),
  CONSTRAINT ck_cases_priority
    CHECK (priority IS NULL OR priority IN ('low', 'medium', 'high'))
);

CREATE TABLE evidence_files (
  id              BIGSERIAL PRIMARY KEY,
  candidate_id    BIGINT      NOT NULL,
  crawl_result_id BIGINT,
  file_type       TEXT        NOT NULL,
  storage_ref     TEXT        NOT NULL,
  content_hash    TEXT,
  captured_at     TIMESTAMPTZ NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_evidence_files_candidate
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE RESTRICT,
  CONSTRAINT fk_evidence_files_crawl_result
    FOREIGN KEY (crawl_result_id) REFERENCES crawl_results(id) ON DELETE RESTRICT,
  CONSTRAINT ck_evidence_files_file_type
    CHECK (file_type IN ('screenshot', 'html_archive', 'dom_snapshot', 'ocr_output', 'favicon', 'logo'))
);

CREATE INDEX ix_risk_scores_candidate_created
ON risk_scores(candidate_id, created_at);

CREATE INDEX ix_rule_results_risk_score
ON rule_results(risk_score_id);

CREATE INDEX ix_rule_results_code
ON rule_results(rule_code);

CREATE INDEX ix_reviews_candidate_reviewed_at
ON reviews(candidate_id, reviewed_at DESC);

CREATE INDEX ix_cases_candidate_status
ON cases(candidate_id, status);

CREATE UNIQUE INDEX uq_cases_candidate_active
ON cases(candidate_id)
WHERE status IN ('open', 'takedown_requested');

CREATE INDEX ix_evidence_files_candidate
ON evidence_files(candidate_id);

CREATE UNIQUE INDEX uq_evidence_files_storage_ref
ON evidence_files(storage_ref);

CREATE INDEX ix_evidence_files_content_hash
ON evidence_files(content_hash)
WHERE content_hash IS NOT NULL;

CREATE TRIGGER trg_cases_set_updated_at
BEFORE UPDATE ON cases
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

COMMIT;
