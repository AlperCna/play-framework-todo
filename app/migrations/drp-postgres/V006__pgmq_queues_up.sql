CREATE EXTENSION IF NOT EXISTS pgmq;

SELECT pgmq.create('candidate_validation_queue');
SELECT pgmq.create('crawl_queue');
SELECT pgmq.create('feature_extraction_queue');
SELECT pgmq.create('similarity_queue');
SELECT pgmq.create('risk_scoring_queue');
