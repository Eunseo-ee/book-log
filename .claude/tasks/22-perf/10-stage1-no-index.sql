-- 1단계: 보조 인덱스 없음(PK만) + 이전 쿼리 형태(함수 사용). 결과 표를 그대로 복사해 붙여주세요.
ALTER TABLE perf_test.content DROP CONSTRAINT IF EXISTS uk_content_member_external_category;
DROP INDEX IF EXISTS perf_test.idx_content_member_view_date;
ANALYZE perf_test.content;
SELECT * FROM perf_test.run_stage('old');
