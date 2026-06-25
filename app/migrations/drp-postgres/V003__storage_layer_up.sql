BEGIN;

CREATE TABLE blob_storage (
  id           BIGSERIAL PRIMARY KEY,
  storage_ref  TEXT        NOT NULL,
  file_type    TEXT        NOT NULL,
  content_type TEXT        NOT NULL,
  data         BYTEA       NOT NULL,
  size_bytes   BIGINT      NOT NULL,
  content_hash TEXT,
  compression  TEXT        NOT NULL DEFAULT 'none',
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_blob_storage_file_type
    CHECK (file_type IN ('html_archive', 'screenshot', 'dom_snapshot', 'ocr_output', 'favicon', 'logo', 'crawl_bundle')),
  CONSTRAINT ck_blob_storage_compression
    CHECK (compression IN ('none', 'gzip')),
  CONSTRAINT ck_blob_storage_size_bytes
    CHECK (size_bytes >= 0)
);

CREATE UNIQUE INDEX uq_blob_storage_storage_ref
ON blob_storage(storage_ref);

CREATE INDEX ix_blob_storage_content_hash
ON blob_storage(content_hash)
WHERE content_hash IS NOT NULL;

CREATE INDEX ix_blob_storage_created_at
ON blob_storage(created_at);

ALTER TABLE blob_storage ALTER COLUMN data SET STORAGE EXTERNAL;

COMMIT;

