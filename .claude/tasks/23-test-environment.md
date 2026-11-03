# 23. 테스트 환경 정리 (CI 전제)

- 번호: 기존 최대 번호 22(`22-성능확인.md`) 다음이라 23 맞음.
- 브랜치: `feat/auth-member`. 커밋하지 않았다. 실제 DB(Supabase) 접속 없음(아래 로그 확인).
- 목표: 비밀 파일(application.yml, application-api-key.yml)과 외부 DB 없이 `./gradlew test`가 통과하는 상태.

## 시작 전 확인
- JWT 설정 위치: `config/JwtProperties.java` (`@ConfigurationProperties(prefix = "jwt")`, `afterPropertiesSet()`에서 검증). 프로퍼티: `jwt.secret`(BASE64, 디코딩 후 32바이트 이상), `jwt.access-token-expiration`, `jwt.refresh-token-expiration`. 값이 없으면 컨텍스트 시작이 실패한다(기본값 없음).
- 외부 API 키: `config/ApiConfig.java` (`prefix = "api"`: `kakao-key`, `tmdb-token`). 필드 초기화 검증이 없어 null이어도 빈 생성은 된다.
- 기타 `@Value`: `SecurityConfig`의 `app.cors.allowed-origins`(기본값 있음).
- 테스트 리소스 폴더(`src/test/resources`)는 없었다.
- `.gitignore`는 `demo/src/main/resources/application.yml`, `application-api-key.yml` 두 개만 막는다. `src/test/resources/application-test.yml`은 `git check-ignore`로 무시되지 않음을 확인.

## 선택한 방식과 이유
- **방식**: `src/test/resources/application-test.yml`(프로파일 `test`) + 두 테스트에 `@ActiveProfiles("test")`.
- **이유**
  1. 프로파일 전용 파일은 main의 `application.yml`이 있어도(내 로컬) 프로파일 설정이 우선해 datasource가 H2로 덮인다. main에 없어도(CI) 그대로 동작한다.
  2. 대안 ① `src/test/resources/application.properties`: 모든 테스트에 자동 적용되지만 main 설정과 이름이 같아 어느 쪽이 이기는지 헷갈리고, `@DataJpaTest`처럼 원래 H2로 도는 테스트까지 영향받는다. ② `@TestPropertySource`를 클래스마다: 중복이 많다. → 필요한 두 클래스에만 명시적으로 켜는 프로파일이 범위가 가장 좁다.
  3. 시크릿은 main에 넣지 않고 test yml에만 가짜 값을 둔다. main 쪽 코드/설정은 건드리지 않았다.
- `@DataJpaTest`의 기본은 `Replace.ANY`(임의 이름의 임베디드 H2로 교체)지만, 그러면 `MODE=PostgreSQL`이 빠진다. 그래서 test yml에 `spring.test.database.replace: none`을 두고 우리가 지정한 H2 URL을 그대로 쓰게 했다. (이 프로퍼티는 `@ActiveProfiles("test")`를 쓴 테스트에서만 적용된다.)
- Redis: `spring.cache.type: simple`을 두었다. 단, `RedisCacheConfig`가 `RedisCacheManager` 빈을 직접 만들기 때문에 이 설정은 Boot 자동 구성에만 영향을 준다(아래 발견 사항).
- H2: `build.gradle`에 `runtimeOnly 'com.h2database:h2'`가 있고 test 런타임 클래스패스는 runtime을 포함하므로 **수정 없이 충분했다**. build.gradle 변경 없음.
- API 키: test yml에 **넣지 않았다**. main yml/api-key yml이 없는 상태에서 컨텍스트가 떠서 null로도 문제없음을 확인했기 때문(더미 값 불필요).

## 변경 파일
| 파일 | 변경 |
|---|---|
| `demo/src/test/resources/application-test.yml` | 신규. H2(MODE=PostgreSQL, DATABASE_TO_LOWER, ddl-auto create-drop), replace none, cache simple, 가짜 jwt 값 |
| `demo/src/test/java/book_log/demo/DemoApplicationTests.java` | `@ActiveProfiles("test")` 추가 |
| `demo/src/test/java/book_log/demo/repository/HighlightRepositoryTest.java` | `@AutoConfigureTestDatabase(replace = NONE)`와 import 제거, `@ActiveProfiles("test")` 추가, 주석에서 "실DB" 표현 정리. 테스트 메서드는 그대로 (CLAUDE.md의 "수정하지 않는다" 규칙은 이번 요청에서 어노테이션 정리만 예외로 허용받음) |
| `.claude/tasks/23-test-environment.md`, `.claude/decisions.md` | 문서 |

JWT 값은 `TestJwt.FAKE_SECRET`과 같은 가짜 문자열("fake-test-only-…")의 BASE64다. 실제 secret은 열람/기록하지 않았다. main yml은 키 이름만 확인했다(값 출력 없음).

## 실행한 검증 (실제 결과)
Docker는 이 PC에서 꺼져 있다(`docker info` 실패).

| # | 상태 | 명령 | 결과 |
|---|---|---|---|
| 1 | main yml 있음(내 로컬) | `./gradlew test --rerun-tasks -i` | BUILD SUCCESSFUL, 223개 실행, 실패 0, 에러 0, **skip 9** |
| 2 | main `application.yml`, `application-api-key.yml` 임시 이름 변경(CI 재현) | 동일 | BUILD SUCCESSFUL, 223개, 실패 0, 에러 0, **skip 9** |

- skip 9개는 전부 `ContentRepositoryPostgresTest`(Testcontainers, Docker 필요). **이번에 실행되지 않았다.** PostgreSQL 쿼리 검증은 Docker가 있는 환경에서만 돈다.
- `DemoApplicationTests` 1개, `HighlightRepositoryTest` 5개 통과(두 실행 모두).
- JDBC URL 로그(`-i` 출력 grep): 두 실행 모두 `jdbc:h2:mem:…`만 나왔고(`booklog-test` 2건 = 우리 test 프로파일, 나머지는 다른 `@DataJpaTest`의 임의 이름 H2), `jdbc:postgresql`, `supabase`, `pooler` 문자열은 **0건**.
- 원복: 2번 실행 직후 `application.yml.bak-task23`, `application-api-key.yml.bak-task23`를 원래 이름으로 되돌렸고, `ls`로 두 파일이 원위치에 있음을 확인했다. `git status`에는 main resources 변경이 없다(gitignore 대상이라 git 기준으로는 원래 안 보인다).
- 실행하지 않은 것: GitHub Actions 실제 실행(워크플로 작성은 범위 밖), Docker 있는 환경에서의 전체 테스트.

## 발견한 문제 (고치지 않음)
1. **H2 비호환 PostgreSQL 전용 쿼리**: `ContentRepository`의 `nativeQuery = true` 두 개 — `findActiveDays`(`EXTRACT(DAY FROM …)` + `CAST … AS INTEGER`, 라인 65~71)와 `findMostActiveDay`(`EXTRACT(DOW …)`, `LIMIT 1`, 라인 104~107). 특히 `DOW` 값은 H2와 PostgreSQL이 다르게 해석할 수 있어 H2로 통과해도 운영 동작을 보장하지 못한다. 이 쿼리의 검증은 `ContentRepositoryPostgresTest`(Docker 필요)가 맡는다.
2. `src/test/java/book_log/demo/DemoApplication.java`가 main과 **같은 FQCN**(`book_log.demo.DemoApplication`)이다. 테스트 클래스패스가 main보다 먼저라 `@SpringBootApplication` 탐색/`HighlightRepositoryTest`의 import가 어느 쪽을 쓰는지 불명확하다.
3. `src/test/java/.../service/injectMocks.java`는 비어 있는 `@interface`로 정체불명 파일이다.
4. `RedisCacheConfig`가 `RedisCacheManager`를 직접 빈으로 만들어 `spring.cache.type: simple`이 무시된다. 테스트가 Redis로 붙지 않는 것은 Lettuce가 지연 연결이라서이며(시작 시 연결 안 함), 캐시를 실제로 타는 `@SpringBootTest`가 생기면 CI에 Redis가 없어 실패할 수 있다.
5. main `application.yml`에 datasource `username`이 값으로 들어 있다(키 이름만 확인, 값은 출력하지 않음). 비밀번호는 api-key 파일로 분리돼 있지만 사용자명도 환경변수나 api-key 파일로 옮기는 편이 안전하다. gitignore 대상이라 저장소에는 올라가지 않으나, 이 파일이 실수로 추적되면 노출된다.
6. `ContentRepositoryPostgresTest`는 Docker 없으면 조용히 skip된다(task 20 결정). CI에서 Docker가 없으면 PostgreSQL 쿼리 검증이 통째로 빠진다는 사실을 인지해야 한다.
7. H2의 `MODE=PostgreSQL`은 문법 일부만 흉내 낸다. 현재 DB 테스트는 단순 저장/조회/제약 위반이라 통과하지만, 부분 인덱스·`ILIKE`·JSON 함수 같은 것을 쓰기 시작하면 H2에서 깨지거나 다르게 동작한다.

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: `DemoApplicationTests`와 `HighlightRepositoryTest`가 실제 DB(Supabase)에 붙고, 설정 파일이 gitignore 대상이라 CI에는 없다. CI에서는 실패하거나 운영 DB를 건드릴 수 있었다.
2. **고려한 선택지**: ① Testcontainers(PostgreSQL) ② 임베디드 H2 + 테스트 프로파일 ③ 설정 파일을 CI 시크릿으로 주입.
3. **선택 이유**: ②. Docker 의존이 없고 빠르며 현재 DB 테스트가 단순 저장/조회라 충분하다. 트레이드오프는 PostgreSQL 전용 문법과 동작이 달라 그 부분은 검증하지 못한다는 것(그래서 네이티브 쿼리는 기존 Testcontainers 테스트가 담당). ③은 CI에 실DB 접속이 남아 목표에 어긋난다.
4. **막혔던 부분과 해결**: `@DataJpaTest`는 기본으로 datasource를 임의 H2로 바꿔 `MODE=PostgreSQL`을 잃는다. `spring.test.database.replace: none`을 test 프로파일에 두어 우리 URL을 쓰게 했다. 로컬에서는 main yml이 있어도 프로파일 설정이 우선해 H2로 붙는지를, yml 이름 변경 실행과 JDBC URL 로그로 확인했다.

## 다음 후보
- CI 워크플로 작성(Docker 유무 결정), 발견 2·3번 파일 정리, 네이티브 쿼리를 Testcontainers로 계속 검증할지 결정.

## 복습 질문
(토글 안에 답을 적어주세요. 프론트 질문은 없다.)

<details><summary>Q1. `@DataJpaTest`와 `@SpringBootTest`는 무엇이 다른가요? 이번에 `HighlightRepositoryTest`에는 `@ContextConfiguration`으로 클래스를 직접 지정했는데 왜 그랬을까요?</summary>

(여기에 답)

</details>

<details><summary>Q2. 이번에 Testcontainers 대신 H2를 고른 이유와 H2의 한계는 무엇인가요? `@DataJpaTest`의 기본 `Replace.ANY`와 우리가 쓴 `replace: none`은 어떻게 다른가요?</summary>

(여기에 답)

</details>

<details><summary>Q3. (기존 코드 연결) `StatisticsService`가 쓰는 쿼리(`findActiveDays`, `findMostActiveDay` 등)를 H2로 테스트하면 왜 위험한가요? 특히 `EXTRACT(DOW …)`를 예로 들어 설명해보세요.</summary>

(여기에 답)

</details>
