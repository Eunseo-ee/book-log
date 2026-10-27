# 16. Content ↔ Member 모델링

- 브랜치: 작업 시작 시 `git status` 깨끗함. 커밋·push 없음.
- 참고: `.claude/decisions.md`, `.claude/tasks/13-member-auth.md`, `.claude/tasks/14-jwt-auth.md`
- 서버(bootRun)는 실행하지 않았다. 운영(Supabase) DB에는 어떤 SQL도 실행하지 않았다.

## 보고할 실수·예외
- **설정 파일 출력:** 작업 초반 구조 파악을 위해 `application.yml`을 `cat`으로 통째로 출력했다. datasource 접속 URL/사용자명 줄이 내 작업 화면에 한 번 보였다(추적 파일·이 문서에는 쓰지 않음). 비밀번호/키가 든 `application-api-key.yml`은 값을 출력하지 않고 키 이름만 확인했다. task 14에서 한 번 보고한 것과 같은 종류의 실수라, 마음에 걸리면 Supabase 접속 정보 교체를 검토.
- **CLAUDE.md와 이번 지시가 충돌한 곳:** CLAUDE.md는 "기존 HighlightRepositoryTest는 수정하지 않는다"인데 이번 지시는 "member를 넣도록 최소 수정"이었다. 이번 지시를 따랐다(`Content.member`가 `nullable=false`라 수정 없이는 컴파일은 돼도 INSERT가 실패한다). 변경은 `saveMember()` 헬퍼 추가와 `Content.builder().member(...)` 추가뿐.

## 0. 기존 Member 파악 (수정하지 않음)
| 항목 | 내용 |
|---|---|
| 패키지 | `book_log.demo.domain.Member` (`extends BaseTimeEntity` → createdAt/modifiedAt) |
| 테이블 | `@Table(name = "members")` |
| id | `Long`, `@GeneratedValue(IDENTITY)` |
| 생성 방식 | Lombok `@Builder` + `@AllArgsConstructor` + `@NoArgsConstructor(PROTECTED)` (정적 팩토리 없음) |
| 필수 필드 | `username`(nullable=false, unique), `password`(nullable=false, BCrypt 해시), `email`(nullable=false), `role`(nullable=false, 빌더 기본값 `ROLE_USER`, `EnumType.STRING`) |
| 레포지토리 | `MemberRepository extends JpaRepository<Member, Long>` — `findByUsername`, `existsByUsername` |

Content 쪽: 테이블명 `@Table` 없이 기본값 `content`(이번에 `name = "content"`로 명시, 이름은 그대로), id `Long` IDENTITY, 생성은 `@Builder`.

## 배경
`Content.userId`(`Long`, `user_id`, 기본값 `1L`)는 임시 값이었다. 이 상태로는 (1) 사용자 개념이 없어 같은 작품을 다른 사람이 저장하면 `findByExternalIdAndCategory`(Optional)가 여러 행을 만나 `IncorrectResultSizeDataAccessException`이 나고, (2) DB가 "존재하지 않는 회원의 기록"을 막아주지 못한다. task 13에서 Member가 생겼으니 Content를 Member에 연결한다. 인증(JWT에서 memberId 꺼내기)은 범위 밖이라 컨트롤러에서 `memberId = 1L`을 쓴다.

## 설계
| 항목 | 내용 |
|---|---|
| 관계 | `@ManyToOne(fetch = LAZY) @JoinColumn(name = "member_id", nullable = false) Member member` |
| 유니크 | `@Table(uniqueConstraints)` `uk_content_member_external_category (member_id, external_id, category)` — 이름을 직접 줘서 Hibernate 해시 이름(`UKlj4d...`)을 피한다 |
| 인덱스 | `idx_content_member_view_date (member_id, view_date)` |
| 중복 조회 | `findByExternalIdAndCategory` 삭제 → `existsByMemberIdAndExternalIdAndCategory(memberId, externalId, category)` (boolean, 엔티티/TEXT 컬럼을 안 읽음, 여러 행이어도 예외 없음) |
| 서비스 | `isAlreadySaved(memberId, externalId, category)`, `saveContent(memberId, dto)`. 컨트롤러(`ContentController` 2곳)는 `Long memberId = 1L` 임시 값 (CalendarController와 같은 방식) |
| DTO | `ContentRequestDto.toEntity()` → `toEntity(Member member)`. member 없이 Content를 만들 수 없게 한다 |
| 캘린더 | `findActiveDays` 네이티브 쿼리 `c.user_id = :userId` → `c.member_id = :memberId`, `CalendarService`/`CalendarController`의 `userId` 이름만 `memberId`로 변경 |

## 선택과 이유 (면접용)

### 1. 관계: `@ManyToOne(LAZY)` vs `Long memberId` 컬럼 유지
| | `@ManyToOne(LAZY)` (선택) | `Long memberId` 컬럼 |
|---|---|---|
| 무결성 | DB FK가 "없는 회원" 저장을 막음 | 앱 코드가 지켜야 함(실수·직접 SQL·삭제 시 고아 행) |
| 쿼리 | JPQL `c.member.id = :id`는 조인 없이 `member_id` 컬럼 비교 | 단순 |
| 비용 | 엔티티 결합 증가, 저장 시 참조 획득(`getReferenceById`) 필요, 테스트마다 Member 필요 | 거의 없음 |
- 선택 이유: 이 프로젝트는 사용자 데이터가 섞이면 안 되는 서비스라 무결성을 DB가 보장해 주는 쪽을 택했다. 비용(테스트에 Member 필요 등)은 이번에 직접 치렀고 작았다.
- LAZY인 이유: 목록/통계에서 Content만 필요한데 EAGER면 항상 member를 같이 읽는다(N+1 위험). 현재 `ContentResponseDto`는 member를 만지지 않는다.

### 2. 없는 member일 때: `getReferenceById` vs `findById/existsById` 사전 조회
| | `getReferenceById` (선택) | 사전 조회 후 예외 |
|---|---|---|
| 쿼리 | 추가 SELECT 없음(프록시) | 저장마다 PK 조회 1회 |
| 없는 회원 | INSERT에서 FK 위반 → `DataIntegrityViolationException` → 500 | 서비스에서 명확한 예외(예: 400/404) |
| 경쟁 | 사전 조회는 확인~INSERT 사이에 회원이 지워지면 어차피 FK가 마지막 방어선 | 〃 |
- 선택 이유: 어차피 FK가 최종 방어선이어야 하므로 사전 조회는 중복 방어에 가깝고 쿼리 1회가 늘어난다. 회원이 없는 상황은 "클라이언트 입력 오류"가 아니라 "인증된 회원 정보와 DB 불일치"(JWT 연동 후엔 삭제된 회원의 access 토큰, decisions 2026-10-24 참고)라 500/별도 처리로 보는 게 맞다고 판단. 트레이드오프: 지금은 `members`에 id=1이 없으면 저장이 500이 되고 원인은 서버 로그에서만 보인다(아래 "수동 실행 SQL"로 선행 확인). 인증 task(19)에서 "회원 없음 → 401" 같은 의미 있는 응답을 정할 것.

### 3. 동시 요청: 유니크 위반 → 중복 예외로 변환
- 흐름: `exists` 체크 → 둘 다 통과 → INSERT 두 번 → 하나는 유니크 위반. 앱 체크만으로는 막을 수 없어서 DB 제약이 최종 방어선이고, 그 위반을 기존 흐름(`IllegalStateException("이미 저장된 콘텐츠입니다.")` → 400)으로 바꿔 사용자에게는 같은 응답이 간다.
- **변환 위치:** `ContentService.saveContent`의 `contentRepository.save(...)` 호출을 try/catch. 이유: `GenerationType.IDENTITY`는 ID를 INSERT 결과로 받아야 해서 `save()` 안에서 INSERT가 즉시 나가므로, 제약 위반이 이 호출에서 바로 터진다(`saveAndFlush`나 flush 시점 예외 처리가 필요 없음). 서비스 트랜잭션 커밋 시점이 아니라 호출 시점이라 예외가 컨트롤러까지 안 새고 서비스에서 변환된다. 반대로 SEQUENCE 전략으로 바꾸면 INSERT가 커밋/flush로 미뤄져 이 try/catch가 위반을 못 잡으니, 전략을 바꿀 때 같이 봐야 한다.
- **유니크 vs FK 구분:** 원인 예외 체인에서 `SQLException.getSQLState() == "23505"`(unique_violation)일 때만 변환. 제약 이름 비교(`uk_content_member_external_category`)도 가능하지만 DB/Hibernate 버전에 따라 메시지·이름 노출이 달라 깨지기 쉽고, SQLState는 표준 코드라 PostgreSQL·H2 둘 다 같다(FK 위반 23503, NOT NULL 위반 23502). 한계: `content`에 유니크 제약이 더 생기면 23505만으로는 구분이 안 되니 그때 제약 이름 비교를 추가해야 한다. 지금은 PK(IDENTITY)를 빼면 이 제약이 유일하다.
- 변환 후 트랜잭션은 롤백된다(PostgreSQL은 오류 후 같은 트랜잭션에서 쿼리가 불가). 예외를 던지고 끝나므로 문제 없음.

### 4. `external_id`가 null인 직접 입력 콘텐츠
- PostgreSQL 유니크 제약에서 NULL은 서로 다른 값으로 취급되어(`NULLS DISTINCT` 기본) `(member_id, NULL, category)`가 여러 번 들어가도 제약에 걸리지 않는다. 즉 DB는 직접 입력 중복을 막지 않는다.
- 단, 앱의 중복 체크(`existsBy...(memberId, null, category)`)는 파생 쿼리가 `external_id IS NULL`로 바뀌어 같은 회원의 같은 카테고리 직접 입력 두 번째를 "이미 저장됨"으로 막는다. DB 제약과 동작이 다르다 → "발견한 문제" 3번.

## ddl-auto: update가 무엇을 하는가 (코드/설정 근거 + 직접 확인한 범위)
`application.yml`의 `spring.jpa.hibernate.ddl-auto: update`. 직접 확인한 것: 임베디드 H2에서 `DemoApplicationTests`(풀 컨텍스트)를 돌린 로그에 Hibernate가 아래를 생성했다 — `create table content (... member_id bigint not null ...)`, `create index idx_content_member_view_date on content (member_id, view_date)`, `add constraint uk_content_member_external_category unique (member_id, external_id, category)`, `foreign key (member_id)`. 즉 매핑 자체(컬럼·제약·인덱스·FK, 이름)는 의도대로다. **PostgreSQL(Supabase)에서 이미 테이블이 있을 때의 ALTER 동작은 직접 확인하지 못했다.** 아래는 Hibernate `update`의 일반 동작에 기반한 예상이다.

| 항목 | 예상 | 확신 |
|---|---|---|
| (a) `member_id` 컬럼 추가 | `alter table content add column member_id bigint not null` 형태 | 확인 필요(ALTER 문 형태) |
| (a-2) 기존 행이 있을 때 | NOT NULL 컬럼을 DEFAULT 없이 추가하면 PostgreSQL이 거부한다(기존 행의 값이 NULL이 되므로). Hibernate `update`는 DDL 실패를 로그만 남기고 앱을 계속 띄운다(`hibernate.hbm2ddl.halt_on_error` 기본 false) → **앱은 뜨는데 `member_id`가 없어 이후 쿼리가 "column does not exist"로 실패** | DB 규칙은 확실, Hibernate가 계속 진행하는 점은 확인 필요 |
| (b) 유니크 제약 | `alter table content add constraint uk_content_member_external_category unique (...)`. 기존 데이터에 중복 `(member_id, external_id, category)`가 있으면 실패 | 확인 필요 |
| (c) 인덱스 | 이름이 없으면 `create index` 실행 | 확인 필요 |
| FK | `alter table content add constraint FK... foreign key (member_id) references members` — 기존 `member_id` 값이 `members`에 없으면 실패 | 확인 필요 |
| 기존 `user_id` | `update`는 컬럼을 **삭제하지 않는다.** 엔티티에서 빠져도 `user_id`(nullable)는 남는다. 새 INSERT는 `user_id`를 안 채우므로 NULL로 쌓이고 무해하지만 데이터는 지저분해진다 | 확실 |
- 결론: 기존 `content` 행이 있는 상태에서 앱을 띄우면 (a-2)로 깨질 가능성이 높다. **앱을 띄우기 전에 기존 content/highlight를 비워야 한다**(아래 순서).
- **주의:** `HighlightRepositoryTest`와 `DemoApplicationTests`는 기본 설정에서 Supabase에 붙어 같은 `update` DDL을 실행한다(`./gradlew test`만 쳐도 스키마가 바뀐다). 아래 3단계 전에는 일반 `./gradlew test`를 돌리지 말 것.

## 수동 실행 SQL (내가 실행하지 않음 — 직접 실행)
**순서:** ① 서버 정지 → ② 현황 확인 → ③ 기존 데이터 삭제 → ④ id=1 회원 확인/시드 → ⑤ 앱 1회 기동(DDL 반영) → ⑥ 반영 확인 → ⑦ 선택: `user_id` 제거.
임시 `memberId = 1L` 때문에 **`members`에 id=1 행이 있어야 저장이 성공**한다(FK).

```sql
-- ② 현황 (읽기 전용)
SELECT id, username, role FROM members ORDER BY id;
SELECT count(*) AS contents FROM content;
SELECT count(*) AS highlights FROM highlight;

-- ③ 기존 데이터 삭제 (되돌릴 수 없음. 필요하면 먼저 백업/내보내기)
BEGIN;
DELETE FROM highlight;   -- 자식 먼저
DELETE FROM content;
COMMIT;

-- ④ id=1 회원이 없을 때만. 로그인은 불가능한 시드(비밀번호가 BCrypt 형식이 아니라 matches가 항상 false)
INSERT INTO members (id, username, password, email, role, created_at, modified_at)
OVERRIDING SYSTEM VALUE
VALUES (1, 'temp-member', '!login-disabled', 'temp@example.com', 'ROLE_USER', now(), now());
-- id를 직접 넣으면 IDENTITY 시퀀스가 안 움직이므로 이후 가입이 id 충돌하지 않게 맞춘다
SELECT setval(pg_get_serial_sequence('members', 'id'), (SELECT max(id) FROM members));
-- 대안: 이미 가입한 회원이 있으면 그 id를 쓰도록 컨트롤러의 1L을 바꾸는 방법도 있지만 이번 task에서는 건드리지 않았다

-- ⑥ 반영 확인 (⑤ 기동 로그에서 "Error executing DDL"도 함께 확인)
SELECT column_name, data_type, is_nullable
FROM information_schema.columns WHERE table_name = 'content' ORDER BY ordinal_position;

SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint WHERE conrelid = 'content'::regclass;   -- u: uk_content_member_external_category, f: member_id → members

SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'content';

-- ⑦ 선택: 더 이상 쓰지 않는 컬럼 제거
ALTER TABLE content DROP COLUMN IF EXISTS user_id;

-- (⑤에서 member_id 추가가 실패해 있었다면, ③을 한 뒤 수동으로)
-- ALTER TABLE content ADD COLUMN member_id bigint NOT NULL;
-- 이후 앱을 다시 띄우면 update가 FK/유니크/인덱스를 이어서 추가한다
```
- 기존 데이터를 살리고 싶으면 삭제 대신 `member_id`를 NULL 허용으로 추가 → `UPDATE content SET member_id = 1` → `SET NOT NULL` 순서도 가능(이번 지시는 삭제).

## 테스트 변경
- 기존 수정: `HighlightRepositoryTest`(saveMember 헬퍼 + 4곳 member 지정; 별도 `@ContextConfiguration` 변경은 불필요 — `@EnableJpaRepositories(basePackages = "book_log.demo.repository")`가 `MemberRepository`도 등록), `HighlightFilterRepositoryTest`(member 1명 지정), `ContentRequestDtoTest`(userId 기본값 검증 → `toEntity(member)`가 member를 넣는지 검증), `ContentServiceTest`(`MemberRepository` mock 추가, 시그니처 변경, 중복 stub을 `existsBy...`로 교체).
- `CalendarServiceTest`, `StatisticsServiceTest`: 수정 없음(인자 위치/타입이 같아 컴파일·통과). 네이티브 쿼리 `findActiveDays`의 `member_id` 컬럼명은 Mockito로 모킹되어 있어 **자동 테스트로 검증되지 않았다**(실제 쿼리 실행은 사용자가 앱에서 확인 필요).
- 추가(`ContentServiceTest`, Mockito 4개): 다른 member는 같은 externalId+category여도 저장 가능(1번은 중복, 2번은 저장 + 저장된 Content의 member가 2번 프록시) / 동시 요청 유니크 위반(23505)은 중복 예외로 변환 / FK 위반(23503)은 그대로 전파(같은 인스턴스) / SQLException 원인이 없는 무결성 오류도 변환 안 함. 기존 중복 테스트에는 `save`가 호출되지 않는지 `verify` 추가.
- **레포지토리 레벨 유니크 제약 테스트는 작성하지 않았다.** `HighlightRepositoryTest`가 `Replace.NONE`으로 Supabase에 붙는 구조라, 거기에 유니크 위반을 일으키는 INSERT 테스트를 두면 실DB에 DDL과 쓰기를 하게 된다. H2 슬라이스(`HighlightFilterRepositoryTest` 방식)로 만들 수는 있지만 이번 지시 범위 밖.

## 실제 테스트 결과 (직접 실행)
- 일반 `./gradlew test`는 실DB에 DDL을 날리므로 **쓰지 않았다.** 대신 환경변수로 datasource를 임베디드 H2로 덮어썼다(`SPRING_DATASOURCE_URL=jdbc:h2:mem:...`, `DRIVER_CLASS_NAME`, `USERNAME=sa`, `PASSWORD=""`). 따라서 **`HighlightRepositoryTest`/`DemoApplicationTests`가 PostgreSQL에서 통과하는지는 확인하지 못했다**(H2 기준 통과). PostgreSQL 전용 동작(네이티브 쿼리, NOT NULL 추가 DDL 등)은 미검증.
- 로컬 설정에 `jwt.*` 키가 없어(현재 `application.yml`/`application-api-key.yml`에서 키 이름 확인) `DemoApplicationTests`가 `jwt.secret 설정이 없습니다`로 실패했다. 이번 변경과 무관한 설정 문제로 보인다(원본 코드로는 돌려보지 않았다). 검증용으로 가짜 JWT 값을 **환경변수로만** 줬다(파일 수정 없음).
- 1차(위 환경변수 전부): **142개 중 140 통과, 2 실패** — 실패는 `JwtPropertiesTest`의 "시크릿 누락"/"만료 시간 오류" 2개. 내가 준 가짜 `JWT_*` 환경변수가 이 테스트의 "설정 없음" 시나리오를 덮어써서 생긴 것.
- 2차(`JWT_*` 환경변수 없이 `JwtPropertiesTest`만): **5개 모두 통과.**
- 정리: 한 번의 실행으로 전부 통과한 건 아니고, 위 두 실행을 합쳐 142개 전부 통과(실패 0, 스킵 0). 기존 138 + 신규 4.

## 발견한 문제 (고치지 않음 — 다음 task 후보)
1. **사용자 조건이 없는 조회:** `ContentService.getFilteredContents`(→ `findByYearAndCategory`/`findByYear`/`findByCategory`/`findAll`), 통계 쿼리(`countByCategoryInterface`, `getAverageRating`, `countByMonth`, `findMostActiveDay`, `countByGenreInterface`). 회원이 둘이 되면 서로의 기록이 섞인다. `HighlightService.saveHighlight`도 `contentRepository.findById(contentId)`만 하고 작품 소유자 확인이 없어 다른 회원의 작품에 하이라이트를 붙일 수 있다(코드로 확인, 하이라이트 조회 쪽은 읽지 않음).
2. **`isSaved`가 다른 회원의 저장을 포함:** `findByExternalIdIn`(→ `ContentService.findSavedKeys` → 검색 결과의 "저장됨" 표시)에 member 조건이 없다. decisions 2026-10-19 "isSaved의 userId 미반영"이 아직 남아 있고 이번 변경으로 풀리지 않았다.
3. **null `externalId` 중복 체크 불일치:** DB는 NULL을 서로 다르게 보지만 앱 체크는 `IS NULL`로 비교해 같은 회원·카테고리의 두 번째 직접 입력을 막는다(변경 전에도 `findBy...`가 같은 방식이라 존재하던 동작).
4. **테스트가 실DB에 붙음:** `HighlightRepositoryTest`(`Replace.NONE`), `DemoApplicationTests`가 Supabase에 붙어 `./gradlew test`마다 `ddl-auto: update`를 실행한다. CLAUDE.md의 "테스트는 H2"와 다름(backlog에도 있는 항목). 테스트용 프로파일/설정 분리 필요.
5. **로컬 설정에 `jwt.*` 없음:** task 14 기록(키 3개 존재)과 달리 현재 `DemoApplicationTests`가 설정 누락으로 실패한다. 다른 브랜치/환경 차이인지 확인 필요.
6. **`StatisticsController` NPE:** `month`가 `required = false`인데 `month < 1`을 null 체크보다 먼저 해서 `month` 없이 호출하면 언박싱 NPE → 500(catch-all). 의도한 "없으면 이번 달" 로직에 도달하지 못함.
7. **회원 삭제 정책 없음:** FK 때문에 content가 있는 회원은 삭제되지 않는다(cascade 없음). 회원 탈퇴 기능을 만들 때 content/highlight 처리(삭제 vs 보존)를 정해야 한다.
8. **member 없음 → 500:** 위 "선택 2"의 트레이드오프. 인증 task에서 응답 정책 결정 필요.

## 변경 파일
- main: `domain/Content.java`, `repository/ContentRepository.java`, `service/ContentService.java`, `service/CalendarService.java`, `controller/ContentController.java`, `controller/CalendarController.java`, `dto/request/ContentRequestDto.java`
- test: `repository/HighlightRepositoryTest.java`, `repository/HighlightFilterRepositoryTest.java`, `dto/request/ContentRequestDtoTest.java`, `service/ContentServiceTest.java`
- 문서: 이 파일, `.claude/decisions.md`(추가만)
- 수정하지 않음: `Member`, 인증/보안 코드, 프론트, `application*.yml`, `GlobalExceptionHandler`

## DoD
- [x] `Content.userId`(+기본값 1L) 제거, `@ManyToOne(LAZY)` + `member_id` NOT NULL
- [x] 유니크 `(member_id, external_id, category)`, 인덱스 `(member_id, view_date)` 선언 (H2에서 DDL 생성 확인)
- [x] `findByExternalIdAndCategory` 제거 → 회원 조건 포함 exists 쿼리, 서비스·컨트롤러에 memberId 전달
- [x] `findActiveDays`를 `member_id`로 수정(실제 실행 검증은 안 됨)
- [x] 유니크 위반(23505)만 중복 예외로 변환, FK 등은 전파 + 테스트
- [x] 수동 실행 SQL·실행 순서·ddl-auto 분석 기록(PostgreSQL 실동작은 "확인 필요" 표시)
- [x] 전체 142개 통과(H2 덮어쓰기 + 두 번에 나눠 실행, 위 설명 참고)
- [x] 커밋·push 없음, 범위 밖 변경 없음, 운영 DB 변경 없음
- [ ] 사용자가 수동 SQL 실행 후 앱 기동 로그/반영 확인 쿼리 확인 (미완)
