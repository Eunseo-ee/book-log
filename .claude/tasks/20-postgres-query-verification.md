# 20. PostgreSQL 실제 실행 검증 (findActiveDays, findMostActiveDay, 통계 쿼리 전반, mock 모양 정합성)

- 브랜치: `feat/auth-member` (시작 시 task 19 변경이 커밋되지 않은 채 working tree에 남아 있었다. 이번 task 변경과 섞여 보일 수 있음)
- 번호: 디렉터리에 `00`~`14`, `16`~`19`가 있어 다음 번호 20 사용
- 커밋하지 않았다. 쿼리/스키마/서비스 로직은 수정하지 않았다(서비스 파싱도 실제 모양과 일치해서 변경 없음).
- 시작 시 작업 디렉터리가 `class-schedule-generator`(다른 프로젝트)였다. 요청 내용과 `.claude/`는 `book-log-main`에만 있어 이 저장소에서 작업했다. (task 19와 같은 상황)

## 현재 상태 파악 (코드로 확인한 것)
- `ContentRepository`의 쿼리는 모두 `memberId` + `[start, end)` 범위를 받는다(task 17). 통계 쿼리에 회원 필터가 **있다**.
- 기존 리포지토리 테스트가 붙는 DB:
  - `MemberIsolationRepositoryTest`, `HighlightFilterRepositoryTest`: `@DataJpaTest` 기본값(Replace.ANY) → **임베디드 H2**.
  - `HighlightRepositoryTest`: `@AutoConfigureTestDatabase(replace = NONE)` → **application.yml의 실제 DB**(Supabase로 추정, 파일은 읽지 않음). 트랜잭션은 롤백되지만 연결은 실제 DB. → 발견한 문제 1번.
- `findMostActiveDay`는 native 쿼리, 반환 `Object[]`. `StatisticsService`는 `dayResult[0]`을 `Object[]`로 캐스팅(중첩 가정)한 뒤 `((Number) row[0]).intValue()`.
- 기존 `StatisticsServiceTest` mock은 `new Object[]{5, 3L}`(평평한 모양)이었다. 서비스는 중첩을 기대하므로 `dayResult[0]`이 `Integer`라 `instanceof Object[]`가 false → **"기록 없음"으로 조용히 빠졌고**, mostActiveDay를 단언하는 테스트가 없어 초록불이었다.
- 가정 확인: Docker 29.8.2 실행 중(`docker ps` 정상). 가능하므로 구현 진행.

## 변경한 파일
| 파일 | 내용 |
|---|---|
| `demo/build.gradle` | testImplementation 3줄 추가: `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter` (버전은 Boot 3.4.1 BOM = Testcontainers 1.20.4). 기존에 없어서 추가. 그 외 변경 없음 |
| `demo/src/test/.../repository/ContentRepositoryPostgresTest.java` | 신규. 9개 테스트 |
| `demo/src/test/.../service/StatisticsServiceTest.java` | mock을 실제 모양 `{ {5, 3L} }`으로 수정, `getMostActiveDay()` 단언 추가, DOW 0~6 매핑(파라미터화 7건) + 빈 배열 케이스 추가 |
| `.claude/decisions.md` | 항목 추가만 |
| `.claude/tasks/20-postgres-query-verification.md` | 이 파일 |

변경하지 않음: `StatisticsService`, `ContentRepository`, application*.yml(읽지도 않음), `HighlightRepositoryTest` 등 기존 테스트.

## 실제 관측 결과 (Testcontainers에서 실행한 것만. 처음 `postgres:15-alpine`, 이후 17로 재실행해도 findMostActiveDay 관측값은 동일)
`findMostActiveDay` (시드: 2026-03 월요일 4건(3/2 두 건, 3/9, 3/16), 금요일 1건):
- **중첩 배열**이다: 길이 1짜리 `Object[]`, 그 요소가 `Object[]{dow, count}`. 즉 `[[1, 4]]`.
- 행 안의 실제 타입: **dow = `java.math.BigDecimal`**, count = `java.lang.Long`. (EXTRACT가 PostgreSQL 14+에서 numeric을 반환하기 때문. Integer/Double이 아님.)
- 기록 없는 달: `null`이 아니라 **길이 0 배열 `[]`**.
- 동률(월 2건, 금 2건): 에러 없이 한 행만 오며 이번 실행에서는 dow=1(월)이었다. `ORDER BY COUNT(*) DESC LIMIT 1`에 보조 정렬이 없어 동률의 승자는 보장되지 않는다(테스트는 1 또는 5로 단언).
- 요일 매핑: 2026-03-01(일)~03-07(토) 각 날짜의 DOW가 `date.getDayOfWeek().getValue() % 7`과 모두 일치(일=0~토=6). 서비스의 배열 `{"일요일","월요일",...,"토요일"}`과 같은 순서이므로 인덱스가 맞는다.

`findActiveDays`: 시드(3월 a: 2,2,6,9,16일 + 2/28·4/1 경계 + b의 3/20) → `[2, 6, 9, 16]` 오름차순·중복 없음, 경계/타 회원 제외, 요소 타입 `Integer`(CAST 정상). 기록 없는 달과 기록 없는 회원은 빈 리스트.

통계 쿼리:
- `countByCategoryInterface`: `BOOK 3, MOVIE 2` 내림차순. `STR(c.category)` 별칭 프로젝션이 PostgreSQL에서 정상 매핑.
- `countByGenreInterface`: 소설 2, SF 2, **null 1** (genre가 null인 행도 하나의 그룹으로 나온다 → 발견한 문제 4번). count 내림차순 확인.
- `getAverageRating`: 평점 `4,5,3,null,4` → `4.0`(null 제외). 기록 없는 달 → `null`.
- `countByMonth`: 반개구간으로 5건(경계 날짜 제외), 기록 없으면 0.
- `StatisticsService`(실제 리포지토리 연결): 3월 → "월요일", 기록 없는 5월 → "기록 없음".

## 서비스 파싱 vs 실제 모양
**어긋나지 않는다. 서비스는 수정하지 않았다.** 근거: 실제 모양이 `[[BigDecimal, Long]]`이고 서비스가 `dayResult[0]`을 `Object[]`로 읽은 뒤 `(Number)`로 변환하므로 중첩 + BigDecimal 모두 처리된다. 빈 배열은 `dayResult.length > 0` 검사로 "기록 없음". 어긋난 것은 **기존 테스트 mock**(평평한 `{5, 3L}`)뿐이었고, 그래서 mock을 실제 모양으로 고치고 mostActiveDay 단언(금요일, 7개 요일 매핑, 빈 배열)을 추가했다.

## 설계 결정과 이유
1. **Testcontainers + `@ServiceConnection` + `@DataJpaTest(replace = NONE)`**: 운영과 같은 엔진에서 재현 가능하고 깨끗한 DB라 오염이 없다. `@SpringBootTest`는 Redis/Security까지 올라와 무겁고 application.yml(민감정보)을 더 많이 쓰게 되므로 슬라이스 테스트로 했다. `@ServiceConnection`이 컨테이너 접속 정보를 데이터소스에 주입하므로 yml의 URL은 쓰이지 않는다.
2. **스키마는 `@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create")`**: 컨테이너 안에서만 생성. (처음엔 create-drop이었으나 JVM 종료 시 컨테이너가 먼저 사라져 DDL drop 재시도로 30초 대기+에러 로그가 나서 create로 바꿨다.)
3. **PostgreSQL 버전**: Supabase 17.6 확인, 이미지 17(`postgres:17-alpine`)로 변경 후 결과: ContentRepositoryPostgresTest 9개 실행, 실패 0, 에러 0, skip 0 (처음 가정했던 15에서도 같은 9개 통과).
4. **Docker 없는 환경: `@Testcontainers(disabledWithoutDocker = true)`로 skip.** 근거: Docker가 없는 환경(CI, 다른 팀원 PC)에서 전체 `./gradlew test`가 깨지지 않는다. 단점: **Docker가 있는데 연결이 안 되는 경우에도 조용히 skip되고 BUILD SUCCESSFUL**이 된다(실제로 겪음, 아래 4번). 그래서 결과 확인 때 skip 수를 반드시 봐야 한다. 항상 실패하길 원하면 이 옵션을 빼면 된다.
5. **DOW는 `Number`로 받는다**: 서비스가 이미 `(Number).intValue()`라 BigDecimal/Double/Integer 어느 쪽이어도 되고, 테스트도 BigDecimal로 mock해 이를 고정했다.

## 발견한 문제 (고치지 않고 기록만)
1. **`HighlightRepositoryTest`가 실제 DB에 연결된다** (`Replace.NONE`, 이 task 이전부터). 전체 `./gradlew test`를 그대로 돌리면 Supabase에 접속한다(롤백되는 트랜잭션이지만 연결·`ddl-auto: update` 적용 가능성이 있다). 이번 task의 "실DB 접속 금지" 때문에 **이 클래스는 실행하지 않았다**(아래 테스트 결과 참고). 별도 task로 Testcontainers나 H2로 옮기는 것을 권장. CLAUDE.md의 "테스트는 H2" 규칙과도 어긋난다.
2. **Docker 29 엔진 + Testcontainers 1.20.4 비호환**: 기본 docker-java API 버전(1.32)을 엔진(최소 1.40)이 거부해 "Could not find a valid Docker environment"가 난다. 이번에는 저장소를 건드리지 않고 실행 시 `-Dapi.version=1.44`(Gradle init 스크립트로 test JVM에 전달)로 우회했다. 이 설정이 없으면 PostgreSQL 테스트 9개가 모두 skip된다. 영구 해결 선택지: ① Testcontainers 2.x로 올리기(Boot BOM 오버라이드, build.gradle 변경이라 이번 범위 밖) ② `~/.docker-java.properties`에 `api.version=1.44` ③ build.gradle의 test 블록에 systemProperty 추가. 사용자 결정 필요.
3. `findMostActiveDay`의 동률은 보조 정렬이 없어 같은 데이터에서도 결과가 바뀔 수 있다(쿼리 변경은 범위 밖). 예: `ORDER BY COUNT(*) DESC, dow`.
4. `countByGenreInterface`는 genre가 null인 기록을 `genre=null` 그룹으로 반환하고, `StatisticsService`는 그대로 `GenreStat(null, n)`로 내려준다. 프론트가 null 장르를 어떻게 그리는지는 미확인(프론트 미확인).
5. 서비스의 `rowObj instanceof Object[]`가 아니면 에러 없이 "기록 없음"이 된다. 지금은 모양이 맞지만, Spring Data/Hibernate 업그레이드로 모양이 바뀌면 조용히 틀린다. 위 실제-리포지토리 통합 테스트(`serviceMostActiveDayWithRealRepository`)가 그 경우를 잡아 준다.
6. `(member_id, view_date)` 인덱스 실제 사용 여부(EXPLAIN)는 이번에도 확인하지 않았다.
7. IDE(VS Code) 진단이 Testcontainers import를 "resolve 안 됨"으로 표시했다. Gradle 컴파일/실행은 정상이며 IDE 클래스패스 갱신(프로젝트 새로고침)이 필요한 것으로 보인다(Gradle 결과 기준).

## 테스트 결과 (실제 실행)
실행 명령: `./gradlew test -I <init 스크립트> --rerun-tasks` — init 스크립트(저장소 밖 scratchpad)는 ① `HighlightRepositoryTest` 제외(위 1번) ② `api.version=1.44` 전달(위 2번), 두 가지만 한다.
- **전체: 209개 실행, 실패 0, 에러 0, skip 0.** (`HighlightRepositoryTest`는 제외해 집계에 없음. 기존 목록 197개 중 이 클래스 분을 빼고 새 테스트 증가분 포함 209개)
- `ContentRepositoryPostgresTest`: 9개 실행, 통과 9, skip 0 (PostgreSQL 15 컨테이너에서 실행 후, 17 이미지로 변경해 재실행한 결과도 9개 통과, skip 0).
- `StatisticsServiceTest`: 13개 통과 (기존 5 + 신규 8: 매핑 7 + 빈 배열 1).
- 기존 테스트가 깨진 것은 없다. 단, 기존 `getStatistics_Success`의 flat mock은 실제 모양과 달랐으므로 의도(금요일 mock)를 유지한 채 모양만 고쳤다.
- init 스크립트 없이 평범하게 `./gradlew test`를 돌렸을 때의 결과는 **실행하지 않았다**(실DB 접속 위험).

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 통계/캘린더 쿼리가 PostgreSQL 전용 구문(EXTRACT DOW, CAST, GROUP BY 별칭)을 쓰는데 H2/mock으로만 검증됐고, `findMostActiveDay` mock 모양이 실제와 달라 mostActiveDay 버그가 있어도 테스트가 초록불일 수 있었다.
2. **고려한 선택지**: ① Supabase 직접 연결(오염 위험) ② H2 PostgreSQL 모드(엔진 차이로 신뢰 낮음) ③ Testcontainers(운영과 같은 엔진, 깨끗한 DB, Docker 필요).
3. **선택 이유**: ③. 비용은 Docker 의존과 첫 실행 시간이고, skip 조건으로 Docker 없는 환경도 깨지 않게 했다. 대신 skip이 조용해서 결과 확인 때 skip 수를 봐야 한다.
4. **막혔던 부분과 해결**: Docker 29 + 구버전 docker-java가 API 버전 불일치로 연결에 실패하는데, `disabledWithoutDocker` 때문에 에러 없이 skip + BUILD SUCCESSFUL이 되어 처음엔 통과처럼 보였다. 테스트 XML의 skipped 수로 알아채고 API 버전을 지정해 해결. 쉬운 설명: `EXTRACT`의 결과는 정수가 아니라 `BigDecimal`이고 native 쿼리 결과는 `[[값, 값]]`처럼 한 겹 더 싸여 있어서, 서비스가 `Number`로 받고 `Object[]`로 한 번 더 꺼내는 것이다.
