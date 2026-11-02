-- C 점검용, 읽기 전용(SELECT만). 실제 public.content에 유니크 제약/인덱스가 있는지, 이미 중복 행이 있는지 확인.
-- Supabase SQL Editor는 마지막 결과만 보여주므로 (1)~(4)를 하나씩 선택 실행해서 결과를 붙여주세요.

-- (1) 인덱스 목록: uk_content_member_external_category, idx_content_member_view_date가 보여야 한다
SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'content';

-- (2) 제약 목록 (contype: u=unique, f=FK, p=PK)
SELECT conname, contype, pg_get_constraintdef(oid) AS def FROM pg_constraint WHERE conrelid = 'public.content'::regclass;

-- (3) (member_id, external_id, category) 중복 행. 0행이어야 정상
SELECT member_id, external_id, category, count(*) FROM public.content
WHERE external_id IS NOT NULL GROUP BY 1, 2, 3 HAVING count(*) > 1;

-- (4) 참고: 행 수 (10만 건 가정이 아니라 실제 규모 확인용)
SELECT count(*) AS content_rows FROM public.content;
