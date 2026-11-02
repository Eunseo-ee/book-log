-- 2단계: (member_id, view_date) 인덱스 추가 + 쿼리는 이전 형태 그대로(TO_CHAR/EXTRACT 유지).
ALTER TABLE perf_test.content DROP CONSTRAINT IF EXISTS uk_content_member_external_category;
CREATE INDEX IF NOT EXISTS idx_content_member_view_date ON perf_test.content (member_id, view_date);
ANALYZE perf_test.content;
SELECT * FROM perf_test.run_stage('old');
