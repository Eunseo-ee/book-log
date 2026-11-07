# 25-e. 운영 노출 정리 (Swagger / actuator / 헬스체크 경로)

- 번호: 25-d-1 다음 작업. `N-하위번호-이름` 선례(25-a~d)를 따라 `25-e-prod-exposure.md`. 커밋하지 않음.
- 목표: Cloud Run 첫 배포(25-f) 전에 운영에서 외부에 드러나면 안 되는 면(Swagger, actuator, 에러 본문)을 정리하고 헬스체크 경로를 확정.
- 범위 밖: 25-f(배포), 25-g(외부 API 방어), 25-h(스모크/로그 최종 확인), 인증/JWT 로직, Dockerfile.

## 작업 전 현황 (코드 수정 전에 확인한 것)
- 운영 프로파일 이름은 **`prod`**. `SecurityConfig.corsConfigurationSource`가 이미 `Profiles.of("prod")`로 기동 실패를 판단한다(25-d). `application-prod.yml`은 없고 `SPRING_PROFILES_ACTIVE=prod`는 25-f에서 지정해야 한다.
- **Swagger**: `build.gradle`에 `springdoc-openapi-starter-webmvc-ui:2.7.0`. springdoc 관련 설정은 `application.yml`/`application-api-key.yml` 어디에도 없음(키 이름 grep만, 값 미출력) → 모든 프로파일에서 기본값(켜짐). `OpenAPI` 빈이나 어노테이션 커스터마이징도 없음.
- **SecurityConfig permitAll(변경 전)**: `/api/auth/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/actuator/health`, `/actuator/health/**`. 나머지 `authenticated()`. Swagger permitAll은 프로파일과 무관하게 항상 열려 있었다.
- **actuator**: `spring-boot-starter-actuator` 의존성 있음. `application.yml`에 `management.*` **없음** → Boot 기본값에 의존(웹 노출은 health만, show-details=never).
- **task 19와의 일치 여부**: 일치. task 19는 "management 설정 없음 → 기본값, health 외 /actuator/**는 인증 필요"로 기록했고 현재도 같았다. 다만 task 19는 **슬라이스 테스트**였고(핸들러가 안 올라와 health 200을 못 봄), 이번에 처음 실제 앱 컨텍스트로 확인했다. 또 "기본값에 기대는 상태"였던 점은 task 19에서 "코드 변경 없음"으로 남겨 둔 것이라 이번에 고정했다.
- **health indicator(변경 전)**: 의존성/자동 구성 기준 db(DataSource), redis, diskSpace, ping. 변경 전에 실제로 구성요소 목록을 응답으로 찍어 보지는 않았다(show-details=never라 응답에 안 나옴). 이번 테스트에서 redis가 root health에 포함돼 Redis 장애 시 root가 DOWN(503)이 되는 것은 **실행으로 확인**했다.
- **server.error**: 설정 없음 → Boot 기본값(include-message/stacktrace/binding-errors=never). 컨트롤러 예외는 `GlobalExceptionHandler`가 `ErrorResponse(status, message)`로 응답하고 catch-all은 고정 문구만 내린다(스택트레이스는 응답이 아니라 `e.printStackTrace()`로 서버 로그에만).
- **로그/SQL 설정 수준**: 로컬 `application.yml`은 `show-sql: true`, `format_sql: true`, `ddl-auto: update`(gitignore, 운영 이미지에는 없음). 운영은 25-a의 `SPRING_JPA_SHOW_SQL=false`, `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. `logging.*` 설정 없음(기본 INFO).
- **이미지 제약**: `.dockerignore`가 `src/main/resources/application*.yml|yaml|properties`를 막고, 25-a는 "운영 설정은 yml 없이 환경변수". → `application-prod.yml`을 새로 만들면 이미지에 안 들어간다.

## 결정 사항
1. **설정 위치**: `application-prod.yml` 대신 `src/main/resources/ops-defaults.properties`(모든 프로파일) + `ops-prod.properties`(prod 전용)를 만들고 `OperationsExposureConfig`가 `@PropertySource`로 읽는다. 이유: ① `application*` 이름은 `.dockerignore`에 걸려 이미지에서 빠지고 Dockerfile/.dockerignore 변경은 범위 밖 ② 로컬 `application.yml`은 gitignore라 커밋되는 곳이 필요 ③ 환경변수·로컬 yml이 이 값을 덮어쓸 수 있다(@PropertySource는 우선순위가 가장 낮음). 트레이드오프: 설정 출처가 하나 더 늘고 Spring 관례(application-prod.yml)가 아니다. 이 파일들이 이미지에 들어가는지는 `docker build`로 확인하지 않았다(.dockerignore 패턴상 제외되지 않는다는 근거만).
2. **Swagger**: prod에서 `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`. 로컬/dev는 아무것도 바꾸지 않아 그대로 켜짐.
3. **SecurityConfig의 Swagger permitAll 정리 — 프로파일별 분리를 택함**: prod에서는 Swagger 경로를 permitAll하지 않는다. 이유: springdoc이 꺼져도 permitAll을 남기면 요청이 인증 없이 DispatcherServlet까지 가고, 핸들러가 없어 `NoResourceFoundException`이 catch-all(`Exception`)에 잡혀 **500**이 된다(backlog 14번 항목과 같은 뿌리). 닫으면 익명은 `anyRequest().authenticated()`에서 401 JSON으로 끝나고 "그 경로가 존재하는지"도 알려 주지 않는다. 그대로 두는 안은 의미 없는 허용 규칙과 500이 남아 채택하지 않았다. 비 prod는 기존 규칙 그대로(기존 `ProtectedApiAccessTest.swaggerIsPublic` 무수정 통과).
4. **actuator 노출**: `management.endpoints.web.exposure.include=health`로 명시 고정 + `management.endpoints.access.default=none`, `management.endpoint.health.access=unrestricted`(Boot 3.4의 access 설정)로 노출이 실수로 넓어져도 한 겹 더 막는다. **info는 노출하지 않는다**: build/git info 설정이 없어 내용이 비어 있고, 내용이 생기면 버전·커밋이 새는 쪽이 이득보다 크다. 필요해지면 그때 추가.
5. **health 상세 숨김**: `show-details=never`, `show-components=never`. 구성요소별 경로(`/actuator/health/redis`, `/db`, `/diskSpace`)가 200이 아님을 테스트로 고정.
6. **liveness / readiness 분리 — 분리함**: `management.endpoint.health.probes.enabled=true`(Cloud Run은 Boot가 자동 감지하는 플랫폼이 아니어서 명시 필요).
   - `liveness` = `livenessState`만. DB도 Redis도 보지 않는다. 판단 기준: liveness가 DOWN이면 플랫폼이 컨테이너를 **재시작**한다. 재시작은 앱 내부가 망가졌을 때(데드락 등)만 효과가 있고, DB·Redis 같은 외부 장애는 재시작해도 안 낫는데 재시작 폭풍만 만든다.
   - `readiness` = `readinessState` + `db`, **redis 제외**. DB 없이는 모든 API가 실패하므로 필수 의존성이지만, Redis는 25-g에서 "장애 시 외부 API 대체 호출"이 전제인 캐시일 뿐이다(`CacheErrorConfig`가 캐시 예외를 삼키는 구조는 이미 있음). 그래서 Redis는 liveness에도 readiness에도 넣지 않는다.
   - root `/actuator/health`는 기본 구성(redis 포함)을 그대로 둔다: 사람이 보는 전체 상태 용도. **Cloud Run에서 이 경로를 쓰면 안 된다**(Redis가 죽으면 503). `management.health.redis.enabled=false`로 root에서도 빼는 안은 Redis 상태 신호 자체가 사라져 채택하지 않았다.
7. **헬스체크 인증**: SecurityConfig의 `/actuator/health`, `/actuator/health/**` permitAll은 task 19의 것을 그대로 유지(변경하면 25-b의 health 확인과 기존 테스트 가정이 바뀜). health 외 `/actuator/**`는 노출도 안 되고 `anyRequest().authenticated()`로 401. 좁혀서 liveness/readiness만 permitAll하는 안은 보류(발견 사항 2).
8. **server.error**: 이미 Boot 기본값이 안전했지만(include-message/stacktrace/binding-errors=never, include-exception=false) 기본값에 기대지 않도록 같은 값을 `ops-defaults.properties`에 고정하고 테스트로 확인.

## 변경 파일
| 파일 | 내용 |
|---|---|
| `demo/src/main/resources/ops-defaults.properties` | 신규. actuator 노출/access/health 상세/probe 그룹, server.error 고정 |
| `demo/src/main/resources/ops-prod.properties` | 신규. prod에서 springdoc api-docs, swagger-ui 비활성 |
| `demo/src/main/java/book_log/demo/config/OperationsExposureConfig.java` | 신규. 위 두 파일을 `@PropertySource`로 읽음(prod 전용은 `@Profile("prod")` 중첩 클래스) |
| `demo/src/main/java/book_log/demo/config/SecurityConfig.java` | `filterChain`이 `Environment`를 받아 prod에서는 Swagger permitAll 생략 |
| `demo/src/test/java/book_log/demo/config/ProdExposureTest.java` | 신규. prod 프로파일 실제 컨텍스트 테스트 |
| `demo/src/test/java/book_log/demo/config/LocalSwaggerTest.java` | 신규. 비 prod에서 Swagger 유지 |
| `.claude/tasks/25-e-prod-exposure.md`, `.claude/decisions.md` | 신규 / 항목 추가 |

application.yml, application-api-key.yml, Dockerfile, .dockerignore, ci.yml, 기존 테스트, JWT/인증 로직은 건드리지 않았다.

## 추가한 테스트 (39개)
`ProdExposureTest` (`@SpringBootTest` + `@ActiveProfiles({"test","prod"})`, H2/가짜 값, Redis는 닫힌 포트로 향하게 함)
| 확인 | 결과 |
|---|---|
| springdoc `OpenAPIService` 빈이 없음, 프로퍼티 false | 통과 |
| `/swagger-ui.html`, `/swagger-ui/index.html`, `/v3/api-docs`, `/v3/api-docs/swagger-config` 익명 → 401 | 통과 |
| 같은 경로에 토큰이 있어도 200 아님, 본문에 openapi/paths 없음 | 통과 |
| `WebEndpointsSupplier`로 본 노출 엔드포인트 = health 하나 | 통과 |
| `/actuator`, env, beans, configprops, heapdump, threaddump, mappings, loggers, info, metrics, shutdown 익명 → 401 | 통과 |
| 같은 경로에 토큰이 있어도 200 아님(노출 안 됨), 본문에 propertySources 등 없음 | 통과 |
| `/actuator/health/liveness`, `/readiness` 익명 200, 본문이 정확히 `{"status":"UP"}` | 통과 |
| **Redis 연결 실패 상태**: root `/actuator/health`는 503 DOWN, liveness·readiness는 200 | 통과 |
| `/actuator/health/redis`, `/db`, `/diskSpace`가 200 아님, DB 종류·디스크 문자열 없음 | 통과 |
| server.error 프로퍼티 4개 고정, 없는 경로 에러 본문에 trace/Exception/클래스명 없음 | 통과 |

`LocalSwaggerTest` (`@ActiveProfiles("test")`): springdoc 빈 있음, `/v3/api-docs` 익명 200 + openapi/paths 존재, `/swagger-ui.html` 200/302, `/swagger-ui/index.html` 200, 노출 엔드포인트 health 하나 — 통과.

**Redis 장애 확인 방식**: Redis 모킹이 아니라 실제 `LettuceConnectionFactory`가 닫힌 포트(127.0.0.1:1)로 연결하게 하고(timeout 500ms) 실제 앱 컨텍스트의 health를 호출했다. 로그에 `RedisReactiveHealthIndicator : Redis health check failed`가 찍혀 장애가 실제로 만들어졌음을 확인했고, root가 503이 아니었다면 테스트가 실패하도록 선행 단언을 뒀다. **확인하지 못한 것**: Redis가 "연결 거부"가 아니라 "응답 없이 멈춤"인 경우(Lettuce 기본 명령 타임아웃은 길다) root health가 오래 걸리는지, 그리고 Upstash(TLS)로의 실제 장애 양상.

## 검증 결과 (실행한 것만)
- `./gradlew cleanTest test`(demo/): **BUILD SUCCESSFUL, 287개 실행, 실패 0, 에러 0, skipped 9**. 이전 248개 + 신규 39개. skipped 9는 이전과 같은 `ContentRepositoryPostgresTest`(Testcontainers가 Docker를 인식 못 함, 통과 미확인 상태 그대로).
- 기존 인증/CORS 테스트(`ProtectedApiAccessTest`, `JwtSecurityChainTest`, `CorsConfigTest`, `CorsOriginsTest` 등) 수정 없이 통과.
- 실행하지 않은 것: `docker build`/컨테이너 기동(이미지에 ops-*.properties가 들어가는지), 실제 Supabase·Upstash 연결, `application.yml`/`application-api-key.yml`이 **없는** 환경(CI와 같은 조건)에서의 신규 테스트. 신규 테스트가 쓰는 값은 `application-test.yml`과 `properties`로 모두 덮어쓰지만, 로컬에서는 main yml이 존재하는 상태로 돌렸다. CI 실행 결과는 push 후 확인 필요.
- 중간에 1번 실패: root health를 `{"status":"DOWN"}`과 **엄격 비교**했더니 `groups` 필드가 더 있어서 실패(발견 사항 2). Redis는 의도대로 DOWN이었고 단언만 status·components·details 기준으로 고쳤다. `cleanTest` 한 번은 Windows 파일 잠금으로 실패했고 재실행에서 통과했다.

## 막힌 부분과 해결
- 처음에는 `application-prod.yml`을 만들려 했으나 `.dockerignore`가 `application*`을 막고 25-a가 "yml 없이 환경변수"를 결정해서 이미지에서 사라진다는 것을 확인 → 이름이 `application`으로 시작하지 않는 properties + `@PropertySource`로 변경. `@PropertySource`가 springdoc의 `@ConditionalOnProperty`(자동 구성 조건)보다 먼저 반영될지가 걱정이었는데, 테스트에서 빈이 실제로 안 생기는 것으로 확인했다.
- 슬라이스 테스트(task 19)로는 actuator 핸들러가 올라오지 않아 health 200과 노출 목록을 못 봤다. 실제 컨텍스트 테스트로 바꾸니 `WebEndpointsSupplier`로 "노출된 엔드포인트 목록"을 그대로 단언할 수 있었다.

## 발견한 문제 (고치지 않음)
1. **`SPRING_PROFILES_ACTIVE=prod`를 빠뜨리면 Swagger가 운영에서 전부 열린다.** CORS는 prod를 빠뜨리면 localhost만 허용돼 "안전한 쪽으로 깨지지만", 이번 설정은 prod를 빠뜨리면 **열린 쪽으로** 깨진다. 25-f 체크리스트 최우선 항목. 방어를 겹치려면 25-f에서 `SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false`도 같이 넣는 것을 권장(미검증). 근본 해결(기본을 끄고 로컬에서만 켜기)은 로컬 `application.yml` 수정이 필요해 하지 않았다.
2. root `/actuator/health`(인증 없음)는 상태와 `groups: ["liveness","readiness"]`(그룹 이름)를 내보낸다. 상세는 없지만 인증 없이 Redis/DB 이상이 DOWN으로 드러난다. liveness·readiness만 permitAll로 좁히는 안은 task 19 결정(41번)과 25-b의 root health 확인을 바꾸게 돼서 보류했다.
3. **catch-all이 없는 경로를 500으로 만든다**(backlog 14·24-2 B6, 이번에 또 확인): 로그인한 사용자가 `/actuator/env`(비노출), `/v3/api-docs`(prod에서 꺼짐) 같은 없는 경로를 부르면 404가 아니라 500이고 `printStackTrace`가 로그에 남는다. 익명은 401이라 외부 스캐너 로그 폭주는 토큰이 있을 때만이지만 고쳐야 한다. 25-e 후보로 적혀 있던 항목이나 "다른 문제는 기록만" 규칙에 따라 고치지 않았다. 그래서 테스트도 "404"가 아니라 "200이 아님"으로 단언했다.
4. `SecurityConfig`가 `frameOptions` 비활성화(H2 콘솔용 주석). H2는 `testRuntimeOnly`라 운영에 콘솔이 없는데 X-Frame-Options가 전역으로 꺼져 있다(클릭재킹 방어 없음). 25-h 또는 별도 task에서 prod일 때 켜기 검토.
5. 로그: `GlobalExceptionHandler`의 `e.printStackTrace()`는 표준 에러로 전체 예외를 찍는다. 유니크/FK 위반 등 `DataIntegrityViolationException` 메시지에는 SQL이나 `Key (...)=(값)`이 들어갈 수 있다(이번에 재현하지 않음). `show-sql=false`여도 이 경로로 SQL/값이 로그에 남을 수 있어 25-h에서 최종 확인 필요. 시크릿(JWT, API 키)이 로그에 찍히는 코드는 이번 설정 수준 확인에서는 못 찾았다(코드 전체 감사는 아님).
6. 환경변수/로컬 yml이 `ops-defaults.properties` 값을 **덮어쓴다**는 것은 Spring 규칙에 근거한 설명이고 이 task에서 테스트로 확인하지는 않았다.
7. Boot 3.4의 `management.endpoints.access.default` 속성은 Boot 3.4.1 기준으로 썼고, 이 값이 실제로 막는지(health 200만 확인)는 노출 설정과 겹쳐 있어 단독 효과를 분리해 검증하지 못했다.
8. (이월) Testcontainers skip 9, 프론트 lint 없음 등 이전 task의 항목은 그대로.

## 남은 TODO
- 25-f: `SPRING_PROFILES_ACTIVE=prod` 지정 확인, (권장) `SPRINGDOC_*_ENABLED=false` 이중 설정, 아래 헬스체크 설정 반영.
- 25-f 배포 후 실제 URL로 `/swagger-ui.html`(401), `/actuator/env`(401), liveness/readiness(200) 재확인 — 25-h 스모크에 포함 권장.
- CI(application.yml 없는 환경)에서 신규 테스트 통과 확인.
- 발견 사항 3(catch-all 404), 4(frameOptions) 처리 시점 결정.

## Cloud Run에 넣을 헬스체크 경로/설정 (25-f용 한 줄 요약)
**liveness probe: HTTP `GET /actuator/health/liveness`, startup probe: HTTP `GET /actuator/health/readiness`(DB 포함, Redis 제외), 둘 다 포트는 컨테이너 `PORT`(8080) 그대로·인증 불필요, `/actuator/health`(Redis 포함)는 Cloud Run 프로브에 쓰지 말 것, 환경변수 `SPRING_PROFILES_ACTIVE=prod`(+ `SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false` 권장).**
(startup probe 타임아웃/주기 값은 25-f에서 JVM 기동 시간을 재서 정할 것 — 여기서는 측정하지 않았다.)

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 운영 배포를 앞두고 Swagger가 모든 프로파일에서 인증 없이 열려 있었고, actuator는 설정 없이 Boot 기본값에만 기대고 있었다. 그리고 헬스체크를 어느 경로로 걸어야 할지 정해져 있지 않았는데, 기본 `/actuator/health`는 Redis까지 포함해서 캐시 장애가 컨테이너 재시작으로 번질 수 있었다.
2. **고려한 선택지**: (설정 위치) ① `application-prod.yml` ② 환경변수만 ③ 이름이 다른 properties + `@PropertySource`(채택). (Swagger) ① permitAll 유지 ② prod에서 permitAll 제거(채택). (헬스) ① root health 하나 ② liveness/readiness 분리 + readiness에서 Redis 제외(채택) ③ Redis indicator 자체 비활성화.
3. **선택 이유**: ③설정 위치는 이미지에 yml이 안 들어가는 기존 결정(25-a/b)과 충돌하지 않으면서 커밋되는 곳이 필요해서. Swagger는 꺼진 기능에 permitAll이 남으면 500이 나기 때문. 헬스는 "외부 의존성 장애는 재시작이 아니라 격리(트래픽 제외/우회)로 다룬다"는 원칙이고, Redis는 캐시라 어느 그룹에도 넣지 않는다. 트레이드오프: 설정 출처가 늘고, prod 프로파일을 빠뜨리면 Swagger가 열린다.
4. **막혔던 부분과 해결**: yml이 이미지에서 빠진다는 것을 알고 설정 파일 이름/위치를 바꿨다. 테스트에서 root health를 엄격 비교했다가 `groups` 필드 때문에 실패해 그룹 이름 노출을 알게 됐다.

## 복습 질문
<details><summary>Q1. 운영에서 Swagger(api-docs, swagger-ui)와 actuator를 막거나 최소만 노출하는 이유는? 이번에 prod에서 Swagger를 "비활성화"했는데 SecurityConfig의 permitAll까지 같이 정리한 이유는?</summary>

(여기에 답)

</details>

<details><summary>Q2. Redis가 죽어도 liveness는 UP이어야 하고 컨테이너가 재시작되면 안 되는 이유는? liveness와 readiness에 각각 무엇을 넣었고, Redis는 왜 둘 다에서 뺐나?</summary>

(여기에 답)

</details>

<details><summary>Q3. (기존 코드 연결, 25-a/25-d) 25-a는 운영 설정을 yml 없이 환경변수로 주입하기로 했고 25-d는 prod 프로파일이면 CORS origin 누락 시 기동 실패를 만들었다. 이번 prod 프로파일 설정(ops-prod.properties)은 이것과 어떻게 이어지며, prod를 빠뜨렸을 때 CORS 쪽과 Swagger 쪽의 실패 방향은 어떻게 다른가? 왜 `application-prod.yml`을 쓰지 못했나?</summary>

(여기에 답)

</details>
