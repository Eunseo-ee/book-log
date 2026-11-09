-- 25-f-1. 운영 DB(Supabase) 전체 테이블 스키마 대조 (ddl-auto=validate 배포 전 점검). 읽기 전용: SELECT만 있다. DDL/DML 없음.
-- 90-check-real-schema.sql 은 content 테이블의 인덱스/제약만 본다. 이 파일은 모든 엔티티 테이블(content, highlight, members)을 본다.
--
-- [실행 방법]
--  * Supabase SQL Editor는 "마지막 결과만" 보여 준다. 아래 쿼리를 (A-1) → (A-2) → ... 순서로 "하나씩 선택해서" 실행하고 결과를 기록한다.
--  * 대상 스키마는 모두 public 으로 명시되어 있다. 각 쿼리 위의 "정상 결과"와 같으면 통과다.
--  * 0행이어야 하는 쿼리에서 1행이라도 나오면 배포를 중단한다. 이 파일은 고치는 SQL을 제공하지 않는다
--    (불일치는 결과 그대로 보고하고, 제약/데이터 정리는 코드가 아니라 SQL로 따로 정한다 — 21-1 결정).
--
-- [기대 스키마의 근거 / 동기화]
--  * 아래 EXPECTED_COLUMNS 목록은 엔티티(Content, Highlight, Member, BaseTimeEntity)에서 온 것이다.
--    Spring Boot 기본 네이밍(CamelCase -> snake_case)이 적용된다. 엔티티를 바꾸고 이 목록을 안 고치면
--    SchemaSqlSyncTest 가 실패한다(테이블/컬럼 "이름"만 비교. 타입은 H2와 PostgreSQL이 달라 문서로 관리한다).
--  * ID 전략은 셋 다 IDENTITY 라서 시퀀스를 요구하는 엔티티는 없다 (B 참고).
--  * 타입 열은 information_schema.columns.data_type 값이다. Hibernate 6.6 validate 는 정확한 이름이 아니라 JDBC 타입 "호환"으로 비교한다
--    (varchar 계열끼리, float/double 끼리, 정수는 expected 가 더 좁으면 넓은 DB 타입도 허용, 길이/NOT NULL 은 검사하지 않음).
--    그래서 (A-1)이 TYPE_MISMATCH 를 내도 곧바로 기동 실패라고 단정하지 말고, 아래 "확인 필요" 표시 항목은 보고 후 판단한다.
--    확인 필요: created_at/modified_at 이 timestamp with time zone 인 경우(PG JDBC가 같은 타입 코드로 보고하는지 미확인),
--               rating 이 numeric/real 인 경우, Enum(STRING) 컬럼이 varchar 가 아닌 PostgreSQL enum 타입인 경우.
--    확실한 불일치: id/member_id/content_id 가 bigint 가 아니고 integer 인 경우(Long 은 bigint 를 요구), 컬럼 없음(MISSING).

-- =====================================================================================
-- (A-1) 없는 컬럼/테이블, 타입 불일치.  정상 결과: 0행
--       problem = MISSING(테이블 또는 컬럼이 없음, validate 기동 실패) / TYPE_MISMATCH(타입 비교 필요)
-- =====================================================================================
WITH expected(table_name, column_name, expected_type) AS (VALUES
-- BEGIN EXPECTED_COLUMNS
('content', 'id', 'bigint'),
('content', 'category', 'character varying'),
('content', 'external_id', 'character varying'),
('content', 'title', 'character varying'),
('content', 'author_or_director', 'character varying'),
('content', 'thumbnail_url', 'character varying'),
('content', 'status', 'character varying'),
('content', 'rating', 'double precision'),
('content', 'view_date', 'date'),
('content', 'member_id', 'bigint'),
('content', 'comment', 'text'),
('content', 'genre', 'character varying'),
('content', 'runtime', 'integer'),
('content', 'created_at', 'timestamp without time zone'),
('content', 'modified_at', 'timestamp without time zone'),
('highlight', 'id', 'bigint'),
('highlight', 'text', 'text'),
('highlight', 'content_id', 'bigint'),
('highlight', 'page', 'integer'),
('highlight', 'season', 'integer'),
('highlight', 'episode', 'integer'),
('highlight', 'timestamp', 'character varying'),
('highlight', 'created_at', 'timestamp without time zone'),
('highlight', 'modified_at', 'timestamp without time zone'),
('members', 'id', 'bigint'),
('members', 'username', 'character varying'),
('members', 'password', 'character varying'),
('members', 'email', 'character varying'),
('members', 'role', 'character varying'),
('members', 'created_at', 'timestamp without time zone'),
('members', 'modified_at', 'timestamp without time zone')
-- END EXPECTED_COLUMNS
)
SELECT e.table_name, e.column_name, e.expected_type, c.data_type AS actual_type,
       CASE WHEN c.column_name IS NULL THEN 'MISSING' ELSE 'TYPE_MISMATCH' END AS problem
FROM expected e
LEFT JOIN information_schema.columns c
       ON c.table_schema = 'public' AND c.table_name = e.table_name AND c.column_name = e.column_name
WHERE c.column_name IS NULL OR c.data_type <> e.expected_type
ORDER BY e.table_name, e.column_name;

-- =====================================================================================
-- (A-2) DB에만 있는 "추가 컬럼" (엔티티에 없는 컬럼). validate 는 추가 컬럼을 무시하지만,
--       NOT NULL 이고 기본값이 없으면 엔티티가 그 컬럼을 채우지 않아 INSERT 가 실패한다(예: 예전 user_id 컬럼).
--       정상 결과: 0행.  행이 나오면 is_nullable 과 column_default 를 보고하고 배포 전에 판단한다(이 파일은 고치지 않는다).
-- =====================================================================================
SELECT c.table_name, c.column_name, c.data_type, c.is_nullable, c.column_default
FROM information_schema.columns c
WHERE c.table_schema = 'public'
  AND c.table_name IN ('content', 'highlight', 'members')
  AND (c.table_name, c.column_name) NOT IN (
        ('content', 'id'), ('content', 'category'), ('content', 'external_id'), ('content', 'title'),
        ('content', 'author_or_director'), ('content', 'thumbnail_url'), ('content', 'status'), ('content', 'rating'),
        ('content', 'view_date'), ('content', 'member_id'), ('content', 'comment'), ('content', 'genre'),
        ('content', 'runtime'), ('content', 'created_at'), ('content', 'modified_at'),
        ('highlight', 'id'), ('highlight', 'text'), ('highlight', 'content_id'), ('highlight', 'page'),
        ('highlight', 'season'), ('highlight', 'episode'), ('highlight', 'timestamp'),
        ('highlight', 'created_at'), ('highlight', 'modified_at'),
        ('members', 'id'), ('members', 'username'), ('members', 'password'), ('members', 'email'),
        ('members', 'role'), ('members', 'created_at'), ('members', 'modified_at'))
ORDER BY c.table_name, c.column_name;

-- =====================================================================================
-- (B) 시퀀스. 세 엔티티 모두 @GeneratedValue(strategy = IDENTITY) 이므로 Hibernate validate 가 요구하는 시퀀스는 "해당 없음".
--     대신 코드는 INSERT 에서 id 를 생략하고 DB가 만들어 주길 기대하므로, id 컬럼이 자동 생성(identity 또는 serial 기본값)인지 확인한다.
-- (B-1) id 자동 생성이 없는 테이블.  정상 결과: 0행
-- =====================================================================================
SELECT table_name, column_name, is_identity, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('content', 'highlight', 'members')
  AND column_name = 'id'
  AND is_identity <> 'YES'
  AND (column_default IS NULL OR column_default NOT LIKE 'nextval(%');

-- (B-2) 참고(필수 아님): public 시퀀스 목록. identity/serial 이면 content_id_seq 같은 시퀀스가 보일 수 있다.
--       정상 결과: 판정 기준 없음 (B-1 이 0행이면 통과). 이 목록이 비어 있어도 identity 컬럼이면 정상이다.
SELECT sequencename FROM pg_sequences WHERE schemaname = 'public' ORDER BY sequencename;

-- =====================================================================================
-- (C) 코드가 의존하는 유니크 제약/인덱스/FK/NOT NULL.  validate 는 이런 것을 검사하지 않는다.
-- (C-1) 이름이 고정된 유니크 제약/인덱스.  정상 결과: 0행
--       problem = MISSING(없음) / DEF_MISMATCH(이름은 있으나 컬럼 구성이나 UNIQUE 여부가 다름)
--       * uk_members_email : 가입 동시성 시 Hibernate 가 제약 "이름"으로 이메일/아이디 중복을 구분한다(21-1). 이름이 달라도 안 된다.
--       * uk_content_member_external_category, idx_content_member_view_date : 90번 (1)(2)과 같은 항목이다.
-- =====================================================================================
WITH expected(index_name, table_name, must_be_unique, columns_text) AS (VALUES
-- BEGIN EXPECTED_INDEXES
('uk_members_email', 'members', true, '(email)'),
('uk_content_member_external_category', 'content', true, '(member_id, external_id, category)'),
('idx_content_member_view_date', 'content', false, '(member_id, view_date)')
-- END EXPECTED_INDEXES
)
SELECT e.index_name, e.table_name, i.indexdef,
       CASE WHEN i.indexname IS NULL THEN 'MISSING' ELSE 'DEF_MISMATCH' END AS problem
FROM expected e
LEFT JOIN pg_indexes i
       ON i.schemaname = 'public' AND i.tablename = e.table_name AND i.indexname = e.index_name
WHERE i.indexname IS NULL
   OR i.indexdef NOT LIKE '%' || e.columns_text
   OR (e.must_be_unique AND i.indexdef NOT LIKE 'CREATE UNIQUE INDEX%');

-- (C-2) members.username 유니크(@Column(unique = true)). 이름은 Hibernate 가 정한 해시 이름일 수 있어 이름이 아니라 "컬럼"으로 확인한다.
--       정상 결과: 0행 (행이 나오면 username 유니크가 없다는 뜻)
SELECT 'members.username UNIQUE missing' AS problem
WHERE NOT EXISTS (
  SELECT 1 FROM pg_indexes
  WHERE schemaname = 'public' AND tablename = 'members'
    AND indexdef LIKE 'CREATE UNIQUE INDEX%' AND indexdef LIKE '%(username)');

-- (C-3) 외래키.  정상 결과: 0행
--       content.member_id -> members, highlight.content_id -> content.
--       코드는 FK 위반(SQLState 23503)이 "없는 회원의 기록"을 막아 준다고 가정한다(decisions 2026-10-27).
WITH expected(table_name, column_name, ref_table) AS (VALUES
('content', 'member_id', 'members'),
('highlight', 'content_id', 'content')
)
SELECT e.table_name, e.column_name, e.ref_table, 'FK MISSING' AS problem
FROM expected e
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_constraint c
  JOIN pg_class t ON t.oid = c.conrelid
  JOIN pg_namespace n ON n.oid = t.relnamespace
  JOIN pg_class r ON r.oid = c.confrelid
  JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY (c.conkey)
  WHERE c.contype = 'f' AND n.nspname = 'public'
    AND t.relname = e.table_name AND a.attname = e.column_name AND r.relname = e.ref_table);

-- (C-4) NOT NULL 이어야 하는 컬럼.  정상 결과: 0행
--       validate 는 NOT NULL 을 검사하지 않고, ddl-auto=update 는 데이터가 있는 기존 테이블에 NOT NULL 을 못 붙이는 경우가 있다.
--       content.member_id 는 decisions 2026-10-27 의 "member_id NOT NULL" 가정, 나머지는 엔티티의 nullable = false 이다.
SELECT c.table_name, c.column_name, c.is_nullable AS actual_is_nullable
FROM information_schema.columns c
WHERE c.table_schema = 'public'
  AND c.is_nullable = 'YES'
  AND (c.table_name, c.column_name) IN (
        ('content', 'member_id'), ('highlight', 'text'),
        ('members', 'username'), ('members', 'password'), ('members', 'email'), ('members', 'role'));

-- =====================================================================================
-- (D) 위 제약이 "없다면" 새로 만들 때 막힐 데이터 확인 (모두 0행이어야 정상).
--     중복/위반 행이 나오면: 배포 중단. 정리는 코드가 아니라 SQL로 따로 진행한다(21-1 결정). 이 파일은 삭제/수정 SQL을 제공하지 않는다.
--     (A-1)에서 해당 컬럼/테이블이 MISSING 이면 아래 쿼리는 "column ... does not exist" 에러가 나는 것이 정상이다. 그 쿼리는 건너뛴다.
-- =====================================================================================

-- (D-1) members 이메일 정규화(lower+trim) 기준 중복 -> uk_members_email 을 못 만든다 (21-1 의 (1)과 같음).  정상 결과: 0행
SELECT lower(trim(email)) AS normalized_email, count(*) AS cnt,
       array_agg(id ORDER BY id) AS member_ids
FROM public.members
GROUP BY lower(trim(email))
HAVING count(*) > 1;

-- (D-2) members 이메일이 정규화되지 않은 행 (대문자/앞뒤 공백).  정상 결과: 0행 (21-1 의 (2)와 같음.
--       새 가입은 항상 정규화되어 저장되므로 이 행이 있으면 existsByEmail 이 못 찾는다)
SELECT id, username, email FROM public.members WHERE email <> lower(trim(email));

-- (D-3) members 아이디 중복 -> username 유니크를 못 만든다.  정상 결과: 0행
SELECT username, count(*) AS cnt FROM public.members GROUP BY username HAVING count(*) > 1;

-- (D-4) content (member_id, external_id, category) 중복 -> uk_content_member_external_category 를 못 만든다.  정상 결과: 0행
--       (90번 (3)과 같은 쿼리. external_id 가 NULL 인 직접 입력은 PostgreSQL 이 서로 다른 값으로 보므로 제외한다)
SELECT member_id, external_id, category, count(*) AS cnt
FROM public.content
WHERE external_id IS NOT NULL
GROUP BY member_id, external_id, category
HAVING count(*) > 1;

-- (D-5) content.member_id 가 NULL 인 행 -> NOT NULL 을 못 건다.  정상 결과: 0행 (행이 나오면 cnt 가 0이 아님)
SELECT count(*) AS null_member_rows FROM public.content WHERE member_id IS NULL HAVING count(*) > 0;

-- (D-6) content.member_id 가 members 에 없는 행 (고아 행) -> FK 를 못 만든다.  정상 결과: 0행
SELECT c.id AS content_id, c.member_id
FROM public.content c
LEFT JOIN public.members m ON m.id = c.member_id
WHERE c.member_id IS NOT NULL AND m.id IS NULL;

-- (D-7) highlight.content_id 가 content 에 없는 행 (고아 행) -> FK 를 못 만든다.  정상 결과: 0행
SELECT h.id AS highlight_id, h.content_id
FROM public.highlight h
LEFT JOIN public.content c ON c.id = h.content_id
WHERE h.content_id IS NOT NULL AND c.id IS NULL;
