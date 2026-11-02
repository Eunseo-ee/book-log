-- 3단계: 같은 인덱스 + 범위 조건 쿼리(현재 ContentRepository 형태).
ALTER TABLE perf_test.content DROP CONSTRAINT IF EXISTS uk_content_member_external_category;
CREATE INDEX IF NOT EXISTS idx_content_member_view_date ON perf_test.content (member_id, view_date);
ANALYZE perf_test.content;
SELECT * FROM perf_test.run_stage('range');
