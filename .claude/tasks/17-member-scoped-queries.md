# 17. 회원별 조회 적용 (memberId 조건 + 월/연도 범위 조건)

- 브랜치: main, 커밋·push 없음. 서버(bootRun) 실행 안 함. 운영(Supabase) DB에는 접속·SQL 실행 없음.
- 참고: `.claude/decisions.md`, `.claude/tasks/16-content-member-modeling.md`, `.claude/tasks/14-jwt-auth.md`
- 용어: 처음 지시서의 `userId`는 task 16 이후의 `memberId`(Content.member.id)로 읽는다. (사용자 확인)
- 이전 번호는 16이 맞다(15번 파일은 원래 없음).

## 배경
task 16에서 Content를 Member에 연결하고 저장·중복 체크·캘린더에만 memberId를 넣었다. 조회 경로는 아직 회원 조건이 없어서 회원이 둘이 되면 서로의 기록이 필터 목록·통계·하이라이트·검색의 "저장됨" 표시에 섞인다(task 16 "발견한 문제" 1, 2번). 이번 task는 남은 조회 경로 전부에 memberId를 넣고, 같은 자리의 `YEAR()`/`TO_CHAR()` 조건을 날짜 범위로 바꾼다.

## task 16에서 완료 (이번에 건드리지 않음)
- `Content.userId = 1L` 제거 → `@ManyToOne(LAZY) member` + `member_id NOT NULL`. 따라서 "@Builder가 필드 초기값을 무시해서 userId가 null일 수 있다"는 문제 자체가 없어졌다. 새 테스트 `memberIsRequired`가 builder로 만든 Content의 member가 지정된 값임을 확인한다(`Content.highlights`는 이미 `@Builder.Default`).
- `existsByMemberIdAndExternalIdAndCategory`, `isAlreadySaved(memberId, ...)`, `/api/contents/check`, `saveContent(memberId, dto)`(memberId는 body가 아니라 컨트롤러가 넘김), 유니크 `(member_id, external_id, category)`, 인덱스 `(member_id, view_date)`, 캘린더의 `member_id` 조건.
- 기존 DB 행 처리(삭제 SQL, 순서, ddl-auto 분석)는 task 16 문서에 있다. 이번 task는 스키마를 바꾸지 않았다(엔티티 변경 없음 → ddl-auto: update가 할 일도 없고 기존 데이터 영향도 없음).

## 이번 변경
| 영역 | 변경 |
|---|---|
| 필터 목록 | `getFilteredContents(memberId, year, category)` 4분기 모두 회원 조건. `findByYearAndCategory/findByYear`는 `(memberId, start, end[, category])`, 카테고리만/전체는 `findByMemberIdAndCategory`/`findByMemberId`. `findAll()` 제거 |
| isSaved | `findByExternalIdIn` → `findByMemberIdAndExternalIdIn(memberId, ids)`. `ContentService.findSavedKeys(memberId, ids)`, `SearchService.search(memberId, category, query, page)` |
| 하이라이트 모음집 | `HighlightSpecifications.ownedBy(memberId)` 추가(`content.member.id` 비교). `HighlightService.getHighlights(memberId, year, category)`가 `ownedBy`를 첫 조건으로 건다 |
| 통계 | 쿼리 5개(`countByCategoryInterface`, `countByGenreInterface`, `getAverageRating`, `countByMonth`, `findMostActiveDay`) 모두 `(memberId, start, end)`. `getMonthlyStatistics(memberId, year, month)` |
| 캘린더 | `findActiveDays(memberId, start, end)`. `CalendarService`가 `LocalDate.of(year, month, 1)`로 start/end 계산 |
| 범위 조건 | `TO_CHAR/EXTRACT(YEAR, MONTH)/YEAR()` → `viewDate >= start AND viewDate < end` (반개구간). 연도 필터도 같이 전환(가능했음). `findMostActiveDay`의 `EXTRACT(DOW)`는 SELECT/GROUP BY에 그대로 |
| 컨트롤러 | 서비스 시그니처에 맞춰 `memberId = 1L` 임시값을 넘김(아래 TODO) |

## 설계 결정과 이유 (면접용)

### 1. memberId는 컨트롤러가 정하고 서비스에 명시 파라미터로 전달 (임시로 1L 유지)
- 서비스가 `SecurityContext`를 읽지 않으니 단위 테스트가 쉽고 의존이 보인다. 요청 DTO에서 받지 않는 이유는 클라이언트가 남의 id를 넣어 보낼 수 있기 때문이다.
- 컨트롤러를 `@AuthenticationPrincipal AuthenticatedMember`로 바꾸는 건 하지 않았다. `SecurityConfig`가 아직 `permitAll`이라 토큰 없는 요청은 principal이 null이고, 바꾸면 현재 프론트 호출이 깨질 수 있다. 인증 강제와 함께 task 19에서 한다. 서비스·리포지토리 시그니처는 그대로 쓰고 컨트롤러 한 줄만 바꾸면 된다.

### 2. 사용자 조건은 자바 필터링이 아니라 쿼리 WHERE
- 전체를 읽은 뒤 거르면 데이터가 커질수록 느리고, 거르는 줄을 하나 빼먹으면 곧바로 다른 회원 데이터가 나간다. WHERE에 있으면 쿼리 시그니처가 memberId를 요구하므로 빼먹을 수 없다(컴파일 에러).
- `HighlightSpecifications.ownedBy(null)`은 "조건 없음(null 반환)"이 아니라 `NullPointerException`이다. 다른 Specification은 null이 "필터 없음"이지만 소유자 조건은 필수라서, null이 조용히 전체 회원 노출로 이어지는 걸 막는다(`ownedByRejectsNull` 테스트).

### 3. 월/연도 조건: 함수 대신 반개구간 범위
- `TO_CHAR(view_date, 'YYYY-MM') = ?`처럼 컬럼에 함수를 씌우면 DB가 `(member_id, view_date)` 인덱스를 범위 스캔에 쓰지 못한다. `view_date >= start AND view_date < end`는 인덱스를 쓸 수 있다.
- 끝을 `<= 말일`이 아니라 `< 다음 달 1일`로 두는 이유: 말일 계산(28/29/30/31)이 필요 없고, 12월은 `plusMonths(1)`이 다음 해 1/1을 알아서 만든다. 경계 날짜가 한 범위에만 속한다(1/31은 1월, 2/1은 2월).
- 인덱스를 실제로 타는지는 EXPLAIN으로 확인하지 못했다(운영 DB 미접속). 데이터가 적으면 PostgreSQL이 인덱스 대신 순차 스캔을 고를 수도 있다. "범위 조건이라 탈 수 있다"까지만 확실하다.
- 전월: 기존 로직 유지(`start.minusMonths(1)` ~ `start`)를 `countByMonth(memberId, prevStart, start)`로 넘긴다. 서비스가 `LocalDate` start/end를 계산해 넘기는 방식으로 구현했다.
- **제안만(구현 안 함):** 지금은 서비스 3곳이 각자 start/end를 계산한다. `DateRange(start, end)` 같은 작은 값 객체나 `YearMonth`를 받는 default 메서드로 모으면 반복이 줄지만, 이번 task 범위 밖이라 두지 않았다.

### 4. `findByExternalIdIn` 교체 이유
- 기존 쿼리는 모든 회원의 저장 건을 보므로 B가 저장한 작품이 A의 검색 결과에 "저장됨"으로 뜬다(decisions 2026-10-19 "isSaved의 userId 미반영"의 해소). 회원 조건이 들어간 `findByMemberIdAndExternalIdIn`으로 교체했다.
- isSaved를 캐시에 넣지 않는 기존 결정(2026-10-19)은 그대로다. Provider 캐시 밖에서 매 요청 `memberId`로 채운다.

## 테스트
- 기존 시그니처 수정: `CalendarServiceTest`, `StatisticsServiceTest`(문자열 yearMonth 목 → `LocalDate` 범위 목), `ContentServiceTest`(`findSavedKeys`), `SearchServiceTest`, `HighlightQueryServiceTest`, `CorsConfigTest`, `JwtSecurityChainTest`. `HighlightRepositoryTest`는 수정하지 않았다.
- 신규 `MemberIsolationRepositoryTest`(11개, `@DataJpaTest` 기본 `Replace.ANY` → 임베디드 H2): 필터 4분기 A/B 분리, 같은 externalId+category를 A·B가 각각 저장(제약 위반 없음)·exists는 회원 기준, 검색용 IN 쿼리 분리, 통계 5개 분리, 기록 없는 회원, 캘린더 분리, 월 경계(1/31·2/1), 연 경계(12/31·다음 해 1/1), 하이라이트 모음집 분리(+연도·카테고리 AND), `ownedBy(null)` 예외.
- **기존 리포지토리 테스트의 DB 연결 확인:** `HighlightRepositoryTest`는 `Replace.NONE`이라 `application.yml`의 datasource(Supabase)에 붙고, `DemoApplicationTests`(`@SpringBootTest`)도 같다. `HighlightFilterRepositoryTest`와 이번 신규 테스트는 H2다. `HighlightRepositoryTest`는 `@DataJpaTest`라 테스트마다 트랜잭션이 롤백되지만, `ddl-auto: update`의 DDL은 롤백과 무관하게 실DB에 나간다(task 16 발견 4번과 동일).
- 이번 테스트 실행 전 확인: 일반 `./gradlew test`는 위 두 테스트로 Supabase에 DDL이 나가므로 쓰지 않았다. 세 환경변수(`SPRING_DATASOURCE_URL=jdbc:h2:mem:...`, `..._DRIVER_CLASS_NAME=org.h2.Driver`, `..._USERNAME=sa`, `..._PASSWORD=""`)로 datasource를 H2로 덮어썼다. 로그에 HikariPool이 H2용으로만 열렸다. 다만 `application.yml`에 Redis 호스트 설정이 있어 `DemoApplicationTests`가 Redis 연결을 시도할 수 있다는 점은 통제하지 않았다(컨텍스트 로딩만 하는 테스트라 쓰기는 없을 것으로 보지만 확인하진 않음).

## 실제 테스트 결과 (직접 실행)
- 1차(H2 덮어쓰기 + 가짜 `JWT_*` 환경변수): 153개 중 151 통과, 2 실패. 첫 실행에서는 신규 `monthlyStatisticsAreScopedToMember`도 실패했다. 원인은 요일 숫자가 H2에서 일=1, PostgreSQL에서 일=0으로 달라서 내가 단언한 값(월요일=1)이 H2에서 2였던 것. 쿼리 오류가 아니라 테스트 가정 오류라 요일 숫자 대신 건수·회원별 분리만 단언하도록 고쳤고(요일 매핑은 PostgreSQL에서 확인 필요), 재실행에서 통과.
- 남은 실패 2개는 `JwtPropertiesTest`의 "시크릿 누락"/"만료 시간 오류". 내가 준 가짜 `JWT_*` 환경변수가 "설정 없음" 시나리오를 덮어써서 생긴 것(task 16과 같은 현상).
- 2차(`JWT_*` 없이 `JwtPropertiesTest`만, 같은 H2 덮어쓰기): 5개 모두 통과.
- 정리: 한 번에 전부 통과한 건 아니고 두 실행을 합쳐 153개 전부 통과(실패 0, 스킵 0). 기존 142 + 신규 11. **PostgreSQL에서의 실행(특히 네이티브 쿼리 2개와 `STR()`/날짜 파라미터 바인딩)은 확인하지 못했다.**

## 발견한 문제 (고치지 않음)
1. **하이라이트 쓰기 소유자 검증 없음(범위 밖):** `saveHighlight`는 `contentRepository.findById(contentId)`라 남의 content에도 하이라이트를 붙일 수 있고, `updateHighlight`/`deleteHighlight`는 id만으로 남의 하이라이트를 수정·삭제한다.
2. **`StatisticsController` NPE:** `month`가 `required = false`인데 `month < 1`을 null 체크보다 먼저 해서(`int` 비교로 언박싱) `month` 없이 호출하면 NPE → 500. "없으면 이번 달" 로직에 도달하지 못한다. (코드로 확인, 이번에 실행은 안 함. task 16 발견 6번과 같음)
3. **`findMostActiveDay` 반환 모양과 테스트 목 불일치:** 실제(H2, 이번에 확인)는 `Object[]`의 0번 원소가 `Object[]{dow, count}`로 중첩되어 오고 서비스는 이 모양을 전제로 한다. 그런데 `StatisticsServiceTest`는 `new Object[]{5, 3L}`(평평한 모양)을 목으로 줘서 서비스가 "기록 없음"으로 빠진다. 이 테스트는 `mostActiveDay`를 단언하지 않아 통과한다. 즉 요일 변환 경로를 검증하는 테스트가 없다. PostgreSQL에서도 중첩인지는 확인하지 못함.
4. **요일 숫자 DB별 차이:** PostgreSQL `DOW`는 일=0, H2는 일=1. `convertDowToKorean`은 PostgreSQL 기준(0=일)이라 H2로 돌리면 한 칸 밀린 요일이 나온다. 운영은 PostgreSQL이라 문제 없지만 H2 기반 테스트로는 요일 매핑을 검증할 수 없다.
5. (task 16에서 이어짐) 테스트가 실DB에 붙는 구조(`HighlightRepositoryTest`, `DemoApplicationTests`), 로컬 설정에 `jwt.*` 없음, null `externalId` 중복 체크와 DB 제약 불일치, 회원 삭제 정책 없음, member 없음 → 500.

## 변경한 파일
- main: `repository/ContentRepository.java`, `repository/HighlightSpecifications.java`, `service/ContentService.java`, `service/SearchService.java`, `service/HighlightService.java`, `service/StatisticsService.java`, `service/CalendarService.java`, `controller/ContentController.java`, `controller/HighlightController.java`, `controller/StatisticsController.java`, `controller/SearchController.java`
- test: `repository/MemberIsolationRepositoryTest.java`(신규), `service/CalendarServiceTest.java`, `service/StatisticsServiceTest.java`, `service/ContentServiceTest.java`, `service/SearchServiceTest.java`, `service/HighlightQueryServiceTest.java`, `config/CorsConfigTest.java`, `security/JwtSecurityChainTest.java`
- 문서: 이 파일, `.claude/decisions.md`(추가만)
- 수정하지 않음: 엔티티(`Content`, `Member`), 인증/보안 코드, 프론트, `application*.yml`, `HighlightRepositoryTest`

## 남은 TODO
- **task 19: 임시 `memberId = 1L` 제거** — `@AuthenticationPrincipal AuthenticatedMember`로 교체할 위치:
  - `ContentController.saveContent`, `ContentController.checkDuplicate` (task 16)
  - `CalendarController.getMonthlyActivity` (task 16)
  - 이번에 새로 생김: `ContentController.getFilteredContents`, `HighlightController.getHighlights`, `StatisticsController.getMonthlyStats`, `SearchController.search`
  - 토큰이 없을 때(401) 응답 정책도 task 19에서 결정.
- 하이라이트 save/update/delete 소유자 검증(위 발견 1번).
- 운영 PostgreSQL에서 네이티브 쿼리 2개(`findActiveDays`, `findMostActiveDay`) 실행 확인 + EXPLAIN으로 `(member_id, view_date)` 인덱스 사용 여부 확인.
- `StatisticsServiceTest`에 중첩 `Object[]` 모양의 `findMostActiveDay` 목과 `mostActiveDay` 단언 추가(발견 3번 해소 후).
- 테스트 프로파일 분리(실DB 접속 제거).

## DoD
- [x] 필터 4분기·하이라이트 모음집·통계 5개·검색 isSaved·캘린더에 memberId 조건
- [x] 월/연도 조건을 반개구간 범위로 전환
- [x] A/B 분리, 같은 작품 각자 저장, 월·연 경계 테스트 (H2)
- [x] 전체 153개 통과(H2 덮어쓰기, 두 번에 나눠 실행)
- [x] 커밋·push 없음, 운영 DB 변경 없음, 스키마 변경 없음
- [ ] PostgreSQL 실행·인덱스 사용 확인 (미완)
