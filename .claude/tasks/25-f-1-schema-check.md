# 25-f-1. 운영 DB 스키마 대조 SQL 확장 (ddl-auto=validate 대비)

- 번호: 25-f의 선행 조건이라 `N-하위번호-이름` 선례(21-1, 24-1, 25-d-1)를 따라 `25-f-1-schema-check.md`. 커밋하지 않음.
- 목표: 운영 DB(Supabase)가 `ddl-auto=update`로 쌓인 스키마이므로, `validate` 기동 실패("리비전 Ready 안 됨"만 보임)를 배포 **전에** 모든 엔티티 테이블에 대해 대조할 수단을 만든다.
- **실제 DB에는 접속하지 않았다.** SQL은 사용자가 Supabase SQL Editor에서 직접 실행한다. 이 PC의 로컬 PostgreSQL 서비스도 건드리지 않았다.
- 범위 밖(하지 않음): 90번 수정, DDL/데이터 정리 SQL, Flyway, 엔티티 변경.

## 1. 현황 (코드로 확인한 것)
### 엔티티와 테이블
| 엔티티 | 테이블 | 비고 |
|---|---|---|
| `Content` | `content` | `@Table(name="content", uniqueConstraints=uk_content_member_external_category(member_id, external_id, category), indexes=idx_content_member_view_date(member_id, view_date))` |
| `Highlight` | `highlight` | `@Table` 없음 → 클래스명 기반 `highlight` |
| `Member` | `members` | `@Table(name="members", uniqueConstraints=uk_members_email(email))`. `username`은 `@Column(unique=true)` |
| `BaseTimeEntity` | (테이블 없음) | `@MappedSuperclass`. `createdAt`, `modifiedAt`이 위 세 테이블에 컬럼으로 들어감 |

- `@Embeddable`, `@ElementCollection`, 상속(`@Inheritance`)으로 생기는 별도 테이블은 **없다** (grep 확인). `Content.highlights`는 `@OneToMany(mappedBy)`라 조인 테이블이 없다.
- 컬럼 이름: `application*.yml`에 네이밍 전략 설정 키가 없어(키 이름 grep) Spring Boot 기본 `CamelCaseToUnderscoresNamingStrategy` → snake_case. Hibernate는 **6.6.26**(Gradle 캐시 확인).
- ID 전략: **세 엔티티 모두 `IDENTITY`**. 시퀀스를 요구하는 엔티티가 없어 Hibernate validate의 "시퀀스 존재 검사"는 **해당 없음**. 다만 코드는 INSERT에서 id를 생략하므로 DB의 id 컬럼이 자동 생성(identity/serial)이어야 한다 → (B-1)로 확인.

### 컬럼 매핑 (SQL의 기대 타입 근거)
| 컬럼 | 자바 타입/설정 | 기대 PostgreSQL 타입 |
|---|---|---|
| `*.id`, `content.member_id`, `highlight.content_id` | `Long` | `bigint` (Long은 bigint 필요: integer면 불일치) |
| `content.category`, `content.status`, `members.role` | `@Enumerated(STRING)` | `character varying` |
| `content.external_id/title/author_or_director/thumbnail_url/genre`, `members.username/password/email`, `highlight.timestamp` | `String` | `character varying` |
| `content.comment`, `highlight.text` | `String` + `columnDefinition="TEXT"` | `text` |
| `content.rating` | `Double` | `double precision` |
| `content.runtime`, `highlight.page/season/episode` | `Integer` | `integer` |
| `content.view_date` | `LocalDate` | `date` |
| `created_at`, `modified_at` | `LocalDateTime` | `timestamp without time zone` |

### Hibernate 6.6 validate가 실제로 보는 것 (소스 `AbstractSchemaValidator`/`ColumnDefinitions`/`Dialect.equivalentTypes` 확인)
- **보는 것**: 테이블 존재, 컬럼 존재, 컬럼 타입(JDBC 타입 "호환" 여부), (시퀀스 엔티티가 있다면) 시퀀스 존재/증가값.
- **안 보는 것**: 유니크 제약, 인덱스, FK, NOT NULL, 기본값, 문자열 **길이**(`hasMatchingLength`가 정의만 있고 validate 경로에서 호출되지 않음을 grep으로 확인), DB에만 있는 추가 컬럼.
- 타입 호환 규칙: 같은 JDBC 타입 코드이거나, varchar 계열끼리, float/real/double끼리, numeric/decimal끼리, 정수는 "기대 타입이 더 좁으면 더 넓은 DB 타입 허용"(`isCompatibleIntegralType`), enum 기대 ↔ varchar DB 허용.
- **확인 필요(추측하지 않음)**: ① `timestamp with time zone`인 DB 컬럼이 `LocalDateTime`(기대 timestamp)과 호환되는지(PG JDBC가 같은 타입 코드로 보고하는지 미확인) ② `rating`이 `numeric`/`real`일 때 ③ Enum 컬럼이 PostgreSQL 네이티브 enum 타입일 때. SQL (A-1)은 이런 경우에도 TYPE_MISMATCH 행으로 보여 주고, 사용자가 결과를 가져오면 그때 판단한다.

## 2. 변경 내용
| 파일 | 내용 |
|---|---|
| `.claude/tasks/22-perf/91-check-all-tables.sql` | 신규 (90번 옆). SELECT만. 쿼리 (A-1)(A-2)(B-1)(B-2)(C-1)~(C-4)(D-1)~(D-7). 각 쿼리 위에 "정상 결과" 주석 |
| `demo/src/test/java/book_log/demo/schema/SchemaSqlSyncTest.java` | 신규 테스트 3개 (아래 §4) |
| `.claude/tasks/25-f-cloud-run-first-deploy.md` | §7, §10-0에 새 SQL 연결 (기존 내용 유지) |
| `.claude/tasks/25-f-1-schema-check.md`, `.claude/decisions.md` | 신규 / 항목 추가 |

90번 SQL, 엔티티, 운영 코드는 건드리지 않았다.

## 3. SQL 구성 (사용자가 Supabase SQL Editor에서 실행)
Supabase SQL Editor는 **마지막 결과만** 보여 주므로 쿼리를 하나씩 선택 실행한다. 대상 스키마는 모두 `public`.

| 쿼리 | 확인 내용 | 정상 결과 | 이상일 때 |
|---|---|---|---|
| A-1 | 기대 컬럼(31개)이 information_schema.columns에 있고 타입이 같은가 | 0행 | MISSING = 기동 실패(배포 중단). TYPE_MISMATCH = 위 "확인 필요" 목록 외에는 기동 실패 가능 |
| A-2 | 엔티티에 없는 **추가 컬럼** (예전 `user_id` 같은 것) | 0행 | validate는 무시하지만 NOT NULL+기본값 없음이면 INSERT 실패. is_nullable/column_default를 보고 |
| B-1 | `id`가 identity/serial인가 (시퀀스는 해당 없음) | 0행 | INSERT 실패 가능 → 중단 |
| B-2 | 참고용 시퀀스 목록 | 판정 없음 | — |
| C-1 | `uk_members_email`, `uk_content_member_external_category`, `idx_content_member_view_date` 이름·컬럼·UNIQUE 여부 | 0행 | MISSING / DEF_MISMATCH → 동시 가입/저장 변환 로직이 깨짐 |
| C-2 | `members.username` 유니크(이름이 아니라 컬럼으로) | 0행 | 아이디 중복 방어선 없음 |
| C-3 | FK: `content.member_id→members`, `highlight.content_id→content` | 0행 | "없는 회원의 기록" 방어선 없음 |
| C-4 | NOT NULL이어야 하는 컬럼 6개 | 0행 | 제약 약화 |
| D-1~D-7 | 위 제약을 새로 만들 때 막힐 데이터: 이메일 중복/비정규화, 아이디 중복, content 중복, NULL member_id, 고아 행 | 모두 0행 | 중복이 있으면 **배포 중단. 정리는 코드가 아니라 SQL로 따로 (21-1 결정). 이 task는 수정 SQL을 쓰지 않았다** |

- (A-1)에서 컬럼/테이블이 MISSING이면 해당 컬럼을 쓰는 (D) 쿼리는 "does not exist" 에러가 나는 게 정상이다(건너뜀).
- 90번의 (1)~(4)와 겹치는 것: 90 (1)(2) = 91 (C-1)의 content 두 항목, 90 (3) = 91 (D-4). 90번은 그대로 두며 둘 다 돌려도 된다.

### 실행 요약 (사용자용)
1. Supabase 대시보드 → SQL Editor. 91번 파일을 열어 **쿼리를 하나씩 선택 → 실행 → 결과 기록**(A-1, A-2, B-1, B-2, C-1, C-2, C-3, C-4, D-1~D-7).
2. 0행이어야 하는 쿼리에서 1행이라도 나오면 **배포 중단**하고 결과를 그대로 가져온다(이 파일은 고치지 않는다).
3. A-1의 TYPE_MISMATCH 행은 타입 이름을 그대로 가져온다(위 "확인 필요" 판단용).
4. 모든 항목이 정상이면 25-f §10-0의 이 체크 항목을 체크한다.

## 4. 테스트 (`SchemaSqlSyncTest`, 임베디드 H2, 외부 DB/Testcontainers 없음)
1. `expectedColumnsMatchEntities`: SQL의 `EXPECTED_COLUMNS` 블록(`-- BEGIN/END` 마커 사이)을 정규식으로 파싱해 `테이블.컬럼` 집합을 만들고, test 프로파일(H2, ddl-auto=create-drop)이 엔티티로 만든 PUBLIC 스키마의 `테이블.컬럼` 집합과 **양방향 일치**를 단언한다. 둘 중 하나가 비어 있으면 실패(헛도는 테스트 방지). 이름만 비교, 타입은 SQL 머리말로 문서화.
2. `expectedIndexesExistInEntitySchema`: SQL의 `EXPECTED_INDEXES`에 있는 세 이름이 H2 스키마의 인덱스/제약에 같은 테이블로 존재하는지(엔티티 `@Table`과 SQL 동기화).
3. `sqlFileIsReadOnly`: 주석과 문자열 리터럴을 제외한 SQL 코드에 `insert/update/delete/drop/alter/create/truncate/grant`가 없음을 단언(읽기 전용 보장).

**실패하는지 실제로 확인(변이 실험, 매번 `cleanTest`로 강제 재실행 후 원복)**
| 변이 | 결과 |
|---|---|
| SQL에서 `content.comment` 한 줄 삭제 | 테스트 1 **FAILED** |
| SQL에 엔티티에 없는 `members.nickname` 추가 | 테스트 1 **FAILED** |
| SQL의 인덱스 이름을 `idx_content_wrong_name`으로 변경 | 테스트 2 **FAILED** |
| 원복(diff로 동일 확인) 후 | 통과 |

## 5. 결정과 이유
1. **기대 스키마를 SQL 안 VALUES로 두고 테스트로 엔티티와 동기화.** 사람이 눈으로 대조하는 것보다 정확하고, 엔티티가 바뀔 때 SQL이 낡아지는 것을 막는다. 트레이드오프: 타입은 H2와 PostgreSQL이 달라 자동 비교하지 못한다(이름만). 타입 변경은 SQL 머리말과 §1 표를 사람이 고쳐야 한다.
2. **SELECT만, 수정 SQL은 쓰지 않음.** 운영 데이터를 실수로 바꾸지 않기 위해, 그리고 불일치 해결 방법(타입 변경, 제약 생성, 데이터 정리)은 결과를 본 뒤 사람이 정해야 하기 때문. 읽기 전용은 테스트 3번으로 고정.
3. **validate가 안 보는 것도 확인**: 유니크/인덱스/FK/NOT NULL/추가 컬럼. 코드가 그 제약에 의존하기 때문(21-1: 제약 이름으로 중복 구분, 2026-10-27: FK/NOT NULL, 2026-10-27: SQLState 23503).
4. **A-2(추가 컬럼)를 넣은 이유**: `ddl-auto=update`는 컬럼을 지우지 않으므로, 예전 설계의 NOT NULL 컬럼이 남아 있으면 validate는 통과해도 INSERT가 실패한다(content의 `user_id`→`member_id` 전환 이력, decisions 2026-10-27). validate가 절대 알려 주지 않는 실패라 별도 확인이 필요하다.
5. **시퀀스(B)는 "해당 없음" + id 자동 생성 확인으로 대체**: 전부 IDENTITY.
6. **PostgreSQL 쪽 문법은 실행하지 못했다.** Docker 데몬이 없고(25-f) 로컬 PostgreSQL 서비스가 떠 있었으나 접속 정보를 모르고 운영 외 DB라도 건드리지 않기로 했다. 쿼리는 표준 information_schema/pg_catalog만 쓰지만 **미실행 상태**다.

## 6. 막힌 부분과 해결
- **변이 실험이 처음에 모두 "통과"로 나옴**: Gradle `test` 태스크가 SQL 파일(`.claude/...`)을 입력으로 보지 않아 `UP-TO-DATE`로 건너뛴 것이었다(4초에 끝남). `cleanTest test --tests ...`로 강제 재실행하자 변이가 정상적으로 실패했다. → 발견 1.
- **읽기 전용 테스트의 오탐 가능성**: SQL에 `LIKE 'CREATE UNIQUE INDEX%'` 같은 문자열 리터럴이 있어 `create `가 금지어로 잡힐 수 있다고 보고, 처음부터 주석과 문자열 리터럴을 제거한 뒤 검사하게 작성했다(실제로 오탐을 겪은 것은 아니다).
- H2의 INFORMATION_SCHEMA 이름 대소문자: test 설정(`DATABASE_TO_LOWER=TRUE`)에 의존하지 않도록 비교 시 `lower()`/`upper(table_schema)='PUBLIC'`로 정규화했다.

## 7. 발견한 문제 (고치지 않음)
1. **SQL 파일이 Gradle 테스트의 입력이 아니다.** SQL만 고치고 `./gradlew test`를 돌리면 `UP-TO-DATE`로 건너뛰어 동기화 테스트가 실행되지 않는다(CI는 깨끗한 환경이라 영향 없음, 로컬만). `build.gradle`에서 `test { inputs.file(...) }`로 해결할 수 있으나 범위 밖이라 하지 않았다. 로컬에서는 `cleanTest`와 함께 실행.
2. 타입 동기화는 자동이 아니다(이름만). 엔티티 필드 타입을 바꿔도 테스트는 통과하고 SQL의 expected_type은 낡을 수 있다.
3. `highlight.timestamp`, `highlight.text`, `content.comment`는 SQL 예약어성 이름이다. PostgreSQL에서 컬럼 이름으로는 허용되나(Hibernate도 따옴표 없이 사용) 쿼리 작성 시 따옴표가 필요할 수 있다(이 SQL은 `'timestamp'`를 문자열 값으로만 사용).
4. `Highlight`에 `@Table`이 없어 테이블 이름이 클래스명 기반(`highlight`)이다. 다른 두 엔티티와 달리 이름을 명시하지 않아 네이밍 전략이 바뀌면 달라진다.
5. (25-f에서 이월) 운영 DB가 실제로 어떤 타입으로 쌓였는지는 아직 모른다. 결과는 사용자가 SQL을 실행한 뒤 확인된다.

## 8. 테스트 결과 (실행한 것만)
- `./gradlew cleanTest test`(demo/): **BUILD SUCCESSFUL, 334개 실행, 실패 0, 에러 0, skipped 9** (이전 331 + 신규 3). skipped 9는 이전과 같은 `ContentRepositoryPostgresTest`(Testcontainers가 Docker를 인식 못 함, 통과 미확인).
- 실행하지 않은 것: 91번 SQL의 PostgreSQL 실행(실제 Supabase 포함), CI(application.yml 없는 환경)에서의 신규 테스트, Docker 관련 전부.
- 커밋하지 않음.

## 9. 정리 (면접/트러블슈팅용)
1. **문제 상황**: 운영 DB가 `ddl-auto=update`로 쌓여 있고 운영은 `validate`라, 스키마가 엔티티와 조금만 달라도 컨테이너가 기동 실패한다. Cloud Run에서는 "리비전 Ready 안 됨"으로만 보여 원인을 찾기 어렵다. 기존 90번 SQL은 content 테이블 하나만 봤다. 게다가 validate는 유니크/FK/NOT NULL을 검사하지 않아 코드가 의존하는 제약이 없어도 통과한다.
2. **고려한 선택지**: ① 사람이 엔티티와 DB를 눈으로 대조 ② 로컬 PG에 스키마 덤프를 복원해 validate로 기동(정확하지만 환경 준비가 큼) ③ 읽기 전용 SQL + 기대 스키마를 테스트로 엔티티와 동기화(채택). Flyway 도입은 범위 밖.
3. **선택 이유**: 운영 DB를 건드리지 않고 사용자가 한 번에 실행·판단할 수 있으며, 엔티티가 바뀔 때 SQL이 낡는 것을 테스트가 막는다. 트레이드오프: 타입은 자동 비교가 안 되고(이름만), PostgreSQL에서의 쿼리 실행은 사용자가 처음 한다.
4. **막혔던 부분과 해결**: 변이 실험이 `UP-TO-DATE` 때문에 계속 통과로 나와 테스트가 헛도는 줄 알았다. 입력 파일이 Gradle에 선언되지 않았음을 파악하고 `cleanTest`로 재실행해 실패를 확인했다.

## 10. 복습 질문
<details><summary>Q1. 운영 DB는 ddl-auto=update로 쌓였는데 왜 운영에서 validate로 바꾸면 위험한가? validate가 검사하는 것과 검사하지 않는 것은 각각 무엇이고, 그래서 이번 SQL에 (C)(D)(A-2)를 따로 넣은 이유는?</summary>

(여기에 답)

</details>

<details><summary>Q2. 기대 스키마를 SQL의 VALUES에 두고 H2 테스트로 엔티티와 동기화한 이유는? 이 테스트가 잡는 것과 못 잡는 것(타입, SQL 파일이 Gradle 입력이 아닌 문제)은 무엇인가?</summary>

(여기에 답)

</details>

<details><summary>Q3. (기존 코드 연결, 21-1/25-a) 이메일 유니크 제약 이름(uk_members_email)을 SQL로 확인하는 이유는? 제약이 없거나 이름이 다르면 동시 가입 때 코드에서 어떤 일이 생기고, 25-a의 "운영은 validate" 결정과 어떻게 이어지나?</summary>

(여기에 답)

</details>
