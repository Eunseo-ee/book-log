-- 참고(선택): 유니크 제약 (member_id, external_id, category)만 있고 view_date 인덱스는 없는 상태.
-- 실제 DB의 유니크 인덱스도 member_id가 선두 컬럼이라, 1단계("인덱스 없음")가 실제 운영 상태와 다르다는 점을 확인하기 위한 것.
-- 1·2·3단계를 모두 끝낸 뒤에 실행.
DROP INDEX IF EXISTS perf_test.idx_content_member_view_date;
ALTER TABLE perf_test.content DROP CONSTRAINT IF EXISTS uk_content_member_external_category;
ALTER TABLE perf_test.content ADD CONSTRAINT uk_content_member_external_category UNIQUE (member_id, external_id, category);
ANALYZE perf_test.content;
SELECT 'unique_only_old' AS stage, * FROM perf_test.run_stage('old')
UNION ALL
SELECT 'unique_only_range', * FROM perf_test.run_stage('range');
