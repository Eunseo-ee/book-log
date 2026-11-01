# 19. 인증 적용 마무리 (경로별 접근 제어, 401/403 통일, CORS/actuator 점검, 로그인 사용자 id 전달)

- 브랜치: `feat/auth-member` (시작 시 `git status` 깨끗함, 마지막 커밋 `07cd9c2` task 18)
- 참고: `.claude/decisions.md`(task 14, 17, 18 항목), `tasks/12-security-merge-investigation.md`, `14-jwt-auth.md`, `18-highlight-ownership.md`
- 번호: 디렉터리에 `00`~`14`, `16`~`18`이 있어 다음 번호 19 사용 (`15`는 파일 없음, 요청서의 task 12·17은 존재 확인)
- 커밋하지 않았다. 로그인/가입/토큰 발급 로직, 리포지토리 쿼리, Redis/비동기는 건드리지 않았다.
- 시작 시 작업 디렉터리가 `class-schedule-generator`(다른 프로젝트)였다. 요청 내용(Content/Highlight/Calendar, task 12·17)과 `.claude/`는 `book-log-main`에만 있어 이 저장소에서 작업했다.

## 현재 상태 파악 (코드로 확인한 것)
- `SecurityConfig`: `anyRequest().permitAll()`(개발용), STATELESS, CSRF 비활성, `.cors(withDefaults())` + `CorsConfigurationSource` 빈(`/api/**`), JWT 필터는 `new`로 체인에만 추가. 401/403 핸들러 없음.
- 로그인 사용자를 꺼내는 방식: `JwtAuthenticationFilter`가 `AuthenticatedMember(Long id, String username)`을 principal로 심고, 컨트롤러는 `@AuthenticationPrincipal AuthenticatedMember`로 받는 구조(`JwtSecurityChainTest`의 ProbeController가 이미 이 방식). 새 방식은 만들지 않았다.
- 하드코딩 `memberId = 1L`: Calendar 1곳, Content 3곳, Highlight 4곳, Search 1곳, Statistics 1곳(총 10곳). 서비스는 모두 이미 `memberId`를 받고 있어 컨트롤러 연결만으로 충분했다.
- `StatisticsController`의 `@CrossOrigin("*")`은 이미 없다(2026-10-21 결정으로 제거됨, `grep CrossOrigin src/main` 결과 주석 1줄뿐).
- actuator: `build.gradle`에 `spring-boot-starter-actuator`가 있고 `application.yml`에 `management.*` 설정이 **없다**. Swagger는 springdoc 2.7.0이 있다.

## 변경 내용
| 요구 | 내용 |
|---|---|
| 1. 사용자 id 전달 | 5개 컨트롤러 10곳의 `memberId = 1L` 제거 → `@AuthenticationPrincipal AuthenticatedMember member` + `member.id()` |
| 2. 접근 제어 | `/api/auth/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/actuator/health(/**)` permitAll, 나머지 `authenticated()` |
| 3. 401/403 통일 | `JsonAuthenticationEntryPoint`(401), `JsonAccessDeniedHandler`(403) 신규, `ErrorResponse(status, message)` JSON. `SecurityConfig.exceptionHandling`에 등록 |
| 4. CORS | 코드 변경 없음, 테스트로 확인(아래) |
| 5. actuator | 코드 변경 없음, `health` 외 인증 필요 규칙만 추가. 설정 파일은 수정 안 함 |
| 6. Statistics | 기본값(현재 연/월)을 먼저 채우고 `targetMonth`로 범위 검증 |

## 발견한 점 점검 결과
### 3-확인. GlobalExceptionHandler가 보안 예외를 500으로 삼키는가
- **URL 기반 규칙(`authenticated()`)이 던지는 `AccessDeniedException`/`AuthenticationException`은 해당 없음.** 시큐리티 필터(`AuthorizationFilter` → `ExceptionTranslationFilter`)에서 처리되어 MVC까지 오지 않는다. 그래서 `@RestControllerAdvice`가 못 잡는 것이 정상이고, 위 두 핸들러가 필요한 이유다.
- **컨트롤러/서비스 안에서 던져지는 `AccessDeniedException`은 `handleAllExceptions(Exception)`에 잡혀 500이 된다.** 지금은 `@EnableMethodSecurity`/`@PreAuthorize`가 없어 재현 경로가 없다(미재현, 코드 읽기로만 판단). 나중에 메서드 보안을 쓰면 발생한다.
- 고칠지 판단: **이번엔 고치지 않고 기록만.** 근거: 현재 발생 경로가 없고, 고치려면 핸들러에서 `AccessDeniedException`을 다시 던지거나 403 핸들러를 추가해야 하는데 기존 핸들러 변경은 범위 밖. 앞 task의 `NoResourceFoundException` 500 문제와 같은 뿌리(catch-all)라 함께 다루는 편이 낫다.
- 같은 뿌리의 관측: 보호 경로를 슬라이스에서 호출했을 때 핸들러 없는 URL이 500이 되는 것을 `/actuator/health` 테스트에서 다시 봤다(backlog 14번 항목 확인).

### 4. CORS
- 허용 헤더: `setAllowedHeaders(List.of("*"))`. Spring은 `*`일 때 preflight의 `Access-Control-Request-Headers` 값을 그대로 `Access-Control-Allow-Headers`에 돌려주므로 응답에 `authorization`이 명시된다(테스트로 확인). 브라우저는 `Authorization`을 와일드카드 `*`로 허용하지 않지만, 이 echo 방식이라 문제없다. 헤더를 명시 목록으로 바꾸는 것은 불필요하다고 판단.
- preflight 인증 없이 통과: `.cors()`가 `CorsFilter`를 인가 필터보다 앞에 넣어 OPTIONS를 거기서 응답한다. `authenticated()`로 바꾼 뒤에도 200임을 테스트로 확인(`CorsConfigTest.preflightPassesWithoutAuthentication`).
- 필터 순서: 보안 필터 체인 쪽에서 적용됨(위와 같은 테스트 + 기존 `preflightHandledBySecurityFilter`: 슬라이스에 없는 `/api/contents`도 같은 규칙).
- 401 응답에도 허용 origin이면 `Access-Control-Allow-Origin`이 붙는다(테스트). 브라우저가 401 본문을 읽을 수 있다.
- `@CrossOrigin("*")`: 이미 제거되어 중복/충돌 없음. 기록만.
- 허용되지 않은 origin은 `CorsFilter`가 403을 직접 쓰므로 본문이 `ErrorResponse`가 **아니다**(Spring 기본 "Invalid CORS request"). → 발견한 문제 3번.

### 5. actuator
- `application.yml`에 `management` 설정 없음 → Spring Boot 기본값: 웹으로는 `health`만 노출(`info` 포함 나머지는 기본 비노출). 즉 **현재 노출은 이미 최소**.
- 코드 쪽 방어: `/actuator/health`, `/actuator/health/**`만 permitAll, 그 외 `/actuator/**`는 `authenticated()`. 테스트는 일부러 `env,beans,info`까지 노출시킨 상태에서 401인지 확인했다(노출 설정이 바뀌어도 인증이 막는다).
- `info`는 공개하지 않았다(필요해지면 matcher 한 줄 추가).
- health의 `show-details` 기본값은 `never`라 상세는 안 나간다.
- **application.yml 수정은 하지 않았다.** 명시적으로 고정하고 싶다면 아래 내용을 직접 추가하면 된다(권장, 선택):
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health
  ```
- 파일 내용은 출력/커밋하지 않았다(확인은 `management|actuator` 키워드 grep과 비밀값 마스킹으로 했다).

## 설계 결정과 이유
1. **`/swagger-ui.html`도 permitAll:** 요청은 `/swagger-ui/**`, `/v3/api-docs/**`만이었지만, springdoc의 진입 URL이 `/swagger-ui.html`이고 거기서 `/swagger-ui/index.html`로 리다이렉트한다. 빼면 진입 URL이 401이라 Swagger를 못 쓴다. 한 줄 추가이고 노출 범위는 같은 Swagger UI 정적 리소스다.
2. **`/actuator/health/**`도 포함:** 하위 그룹(liveness/readiness)이 쓰일 때 로드밸런서·배포 환경 점검이 막히지 않게. 상세 정보는 `show-details=never`가 기본이라 새는 것 없음.
3. **핸들러가 `ObjectMapper`를 주입받아 JSON 작성:** 필터 단계 예외는 `@RestControllerAdvice`가 못 잡으므로 같은 `ErrorResponse`를 직접 쓴다. Boot의 `ObjectMapper` 빈을 `filterChain` 파라미터로 받아 `new`로 핸들러를 만든다(JWT 필터와 같은 방식, 빈 등록 없음).
4. **401 메시지는 하나("인증이 필요합니다."):** 토큰 없음/위조/만료/refresh 토큰 사용을 구분해 알려주면 공격자에게 단서가 된다(로그인 실패를 구분하지 않은 결정과 같은 이유). 예외의 내부 사유는 응답에 넣지 않는다(테스트로 고정).
5. **서비스 시그니처 변경 없음:** 모든 서비스가 이미 `memberId`를 받고 있어 "시그니처 변경이 커서 보류"할 곳이 없었다. 따라서 범위 판단 기록 대상도 없음.
6. **principal이 null일 때 방어 코드 없음:** `authenticated()`가 앞에서 막으므로 컨트롤러에 도달하면 principal은 항상 있다. 보안 규칙이 풀리면 NPE가 나지만, 이는 조용히 1L로 동작하는 것보다 낫다(실패가 드러남).
7. **Statistics 검증 순서:** `targetMonth` 계산 후 검증. 에러는 기존 `DateTimeException` → 400을 그대로 유지(핸들러 변경 없음).

## 테스트 변경
- 기존 수정(의도 유지): `CorsConfigTest`(실제 요청 테스트에 인증 추가, preflight/401 CORS 헤더 테스트 2개 추가), `JwtSecurityChainTest`(permitAll 전제였던 2개를 "401 ErrorResponse"로 변경: 잘못된/만료/위조 토큰은 500이 아니라 401, refresh 토큰은 인증 안 됨), `GlobalExceptionHandlerOwnershipTest`(요청에 인증 추가).
- 신규: `ProtectedApiAccessTest`, `JsonSecurityHandlersTest`, `MemberIdPropagationTest`, `StatisticsControllerTest`, 헬퍼 `TestAuth`.
- 인증 심기: `@WithMockUser`는 principal이 `User`라 `AuthenticatedMember`로 변환되지 않아(null → NPE) 쓰지 않고, `authentication(UsernamePasswordAuthenticationToken(AuthenticatedMember...))` post-processor를 `TestAuth.member(id)`로 만들었다.
- 403은 **체인으로 재현할 경로가 없다**(역할로 막는 경로 없음, `ROLE_ADMIN`은 정의만 됨). 그래서 `AccessDeniedHandler`는 직접 호출로만 검증했고, "체인에 등록되어 실제로 동작함"은 테스트하지 않았다.

## 테스트 결과
- `./gradlew test` 전체 **197개 통과, 실패 0, 스킵 0**(`build/test-results` XML 합산). 이전 163개 → +34.
- 첫 실행에서 1개 실패: `/actuator/health` 200 기대. 원인은 WebMvc 슬라이스에 actuator 핸들러가 올라오지 않아 보안은 통과했지만 핸들러가 없어 500(catch-all)이 난 것. 테스트를 "보안에 막히지 않음(401/403 아님)"으로 약화했다.
- **테스트하지 않은 것:**
  - 실제 앱 기동 상태에서 `/actuator/health`가 200이고 `/actuator/env`가 비노출/401인지 (슬라이스라 health의 실제 200 응답은 검증 못 함)
  - Swagger UI가 실제로 열리는지 (슬라이스에 springdoc 없음, 401/403이 아님만 확인)
  - 실제 DB·실제 토큰으로 프론트부터 이어지는 end-to-end
  - `DemoApplicationTests`는 실제 DB에 붙는 기존 테스트이며, 이번 실행에서 통과했다(백로그의 테스트 DB 분리 이슈는 그대로).

## 변경한 파일
- 수정: `config/SecurityConfig.java`, `controller/{Calendar,Content,Highlight,Search,Statistics}Controller.java`, 테스트 `config/CorsConfigTest.java`, `security/JwtSecurityChainTest.java`, `exception/GlobalExceptionHandlerOwnershipTest.java`, `.claude/decisions.md`·`backlog.md`(추가만)
- 신규: `security/JsonAuthenticationEntryPoint.java`, `security/JsonAccessDeniedHandler.java`, 테스트 `security/{TestAuth,ProtectedApiAccessTest,JsonSecurityHandlersTest}.java`, `controller/{MemberIdPropagationTest,StatisticsControllerTest}.java`, 이 파일

## 발견한 문제 (고치지 않음)
1. **프론트 영향(확인 필요):** 이제 모든 `/api/**`(auth 제외)가 토큰을 요구한다. 프론트가 `Authorization: Bearer` 헤더를 붙이지 않으면(또는 Next 프록시가 헤더를 전달하지 않으면) 전부 401이다. 이번 범위에서 프론트는 읽지도 수정하지도 않았다.
2. catch-all `Exception` 핸들러가 컨트롤러/서비스에서 던져진 `AccessDeniedException`도 500으로 만들 수 있음(위 3-확인). 현재 재현 경로 없음.
3. 허용되지 않은 origin의 CORS 거절(403)은 `ErrorResponse` 형식이 아님(Spring `CorsFilter` 기본 응답). 프론트는 브라우저가 CORS 오류로 처리하므로 실질 영향은 작음.
4. `ROLE_ADMIN`이 정의돼 있지만 역할로 막는 경로가 없어 403(권한 부족)이 실제로는 발생하지 않는다. 소유권 403은 서비스의 `ForbiddenResourceException`이 맡는다.
5. `ErrorResponse` 형식이 `MethodArgumentNotValidException`(검증 실패)에서는 `{필드: 메시지}` 맵이라 401/403/기타 오류와 응답 모양이 다르다(기존).
6. `StatisticsController`의 year는 범위 검증이 없다(기존, 이번 수정 대상 아님).
7. 회원이 삭제된 뒤 만료 전 access 토큰으로 `saveContent` 하면 FK 위반 500이 된다(task 16 결정 "응답 정책은 인증 task에서"의 후속). 이번 task에서 정하지 않았다.
8. 에러 응답용 로그: 인증 실패마다 `JwtAuthenticationFilter`의 WARN은 예기치 못한 예외에만 남고 단순 무토큰은 안 남는다(기존 backlog의 위조 토큰 로그 항목과 별개로 모니터링 지점 없음).

## 복습 질문
1. `JsonAuthenticationEntryPoint`는 왜 `GlobalExceptionHandler`에 `AuthenticationException` 핸들러를 추가하는 방식이 아니라 별도 클래스로 만들어 `SecurityConfig`에 등록했을까? 요청이 처리되는 순서(서블릿 필터 → DispatcherServlet)로 설명해 보자.
2. 컨트롤러 테스트에서 `@WithMockUser` 대신 `AuthenticatedMember`를 직접 심는 `TestAuth.member(id)`를 만든 이유는? `@AuthenticationPrincipal AuthenticatedMember` 파라미터에 `@WithMockUser`를 쓰면 무슨 일이 생기는가?
3. (기존 코드 연결) task 18에서 소유권 검사를 컨트롤러가 아니라 서비스에 둔 덕분에, 이번에 컨트롤러가 `1L`에서 `member.id()`로 바뀌었는데도 서비스 코드는 바뀌지 않았다. 반대로 소유권 검사를 컨트롤러에 뒀다면 이번 작업에서 무엇이 달라졌을까? 그리고 이번에 추가한 401(인증 없음)과 task 18의 403(남의 리소스)은 각각 어느 계층에서 만들어지고 왜 응답 JSON 형식이 같을 수 있을까?
