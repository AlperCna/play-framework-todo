BEGIN;

CREATE TABLE crawl_results (
  id               BIGSERIAL PRIMARY KEY,
  candidate_id     BIGINT      NOT NULL,
  http_status      INT         NOT NULL,
  redirect_chain   JSONB       NOT NULL DEFAULT '[]'::jsonb,
  final_url        TEXT        NOT NULL,
  resolved_ip      TEXT,
  asn              TEXT,
  asn_org          TEXT,
  hosting_provider TEXT,
  ip_country       TEXT,
  storage_ref      TEXT        NOT NULL,
  metadata         JSONB       NOT NULL DEFAULT '{}'::jsonb,
  crawled_at       TIMESTAMPTZ NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_crawl_results_candidate
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE RESTRICT,
  CONSTRAINT ck_crawl_results_http_status
    CHECK (http_status >= 100 AND http_status <= 599),
  CONSTRAINT ck_crawl_results_redirect_chain_array
    CHECK (jsonb_typeof(redirect_chain) = 'array')
);

CREATE TABLE page_features (
  id                 BIGSERIAL PRIMARY KEY,
  crawl_result_id    BIGINT      NOT NULL,
  title              TEXT,
  has_form           BOOLEAN     NOT NULL DEFAULT false,
  has_password_input BOOLEAN     NOT NULL DEFAULT false,
  brand_name_found   BOOLEAN     NOT NULL DEFAULT false,
  dom_summary        JSONB       NOT NULL DEFAULT '{}'::jsonb,
  extractor_version  TEXT        NOT NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT fk_page_features_crawl_result
    FOREIGN KEY (crawl_result_id) REFERENCES crawl_results(id) ON DELETE RESTRICT
);

CREATE TABLE candidate_asset_matches (
  id               BIGSERIAL PRIMARY KEY,
  candidate_id     BIGINT       NOT NULL,
  asset_id         BIGINT       NOT NULL,
  crawl_result_id  BIGINT,
  match_type       TEXT         NOT NULL,
  similarity_score NUMERIC(5,4) NOT NULL,
  details          JSONB        NOT NULL DEFAULT '{}'::jsonb,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_candidate_asset_matches_candidate
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE RESTRICT,
  CONSTRAINT fk_candidate_asset_matches_asset
    FOREIGN KEY (asset_id) REFERENCES assets(id) ON DELETE RESTRICT,
  CONSTRAINT fk_candidate_asset_matches_crawl_result
    FOREIGN KEY (crawl_result_id) REFERENCES crawl_results(id) ON DELETE RESTRICT,
  CONSTRAINT ck_candidate_asset_matches_similarity_score
    CHECK (similarity_score >= 0 AND similarity_score <= 1)
);

CREATE TABLE detection_signals (
  id              BIGSERIAL PRIMARY KEY,
  candidate_id    BIGINT       NOT NULL,
  crawl_result_id BIGINT,
  signal_type     TEXT         NOT NULL,
  score           NUMERIC(5,4) NOT NULL,
  details         JSONB        NOT NULL DEFAULT '{}'::jsonb,
  metadata        JSONB        NOT NULL DEFAULT '{}'::jsonb,
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT fk_detection_signals_candidate
    FOREIGN KEY (candidate_id) REFERENCES candidates(id) ON DELETE RESTRICT,
  CONSTRAINT fk_detection_signals_crawl_result
    FOREIGN KEY (crawl_result_id) REFERENCES crawl_results(id) ON DELETE RESTRICT,
  CONSTRAINT ck_detection_signals_score
    CHECK (score >= 0 AND score <= 1)
);

CREATE INDEX ix_crawl_results_candidate_crawled
ON crawl_results(candidate_id, crawled_at);

CREATE INDEX ix_page_features_crawl_result
ON page_features(crawl_result_id);

CREATE UNIQUE INDEX uq_page_features_crawl_extractor
ON page_features(crawl_result_id, extractor_version);

CREATE INDEX ix_candidate_asset_matches_candidate
ON candidate_asset_matches(candidate_id);

CREATE INDEX ix_detection_signals_candidate_signal
ON detection_signals(candidate_id, signal_type);

COMMIT;
