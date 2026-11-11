# 32. 기동 로그의 기본 비밀번호(`Using generated security password`) 제거

- 번호: 직전이 `31`이라 `32`. 커밋하지 않음.
- 범위: `SecurityConfig.java`에 빈 1개 + 신규 테스트 1개 + decisions.md 항목 추가. 인증 방식·정책·로깅 설정은 건드리지 않았다.

## 1. 인증 구조 (근거 포함, 코드로 직접 확인)
- SecurityFilterChain: `SecurityConfig.java:37-78`. csrf 비활성(:42), cors(:45), STATELESS 세션(:48), `/api/auth/**`·Swagger(비 prod)·actuator health만 permitAll, 나머지 `authenticated()`(:54-61), JSON 401/403 핸들러(:64-67), `JwtAuthenticationFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 추가(:70).
- 로그인 방식: JWT(access/refresh). `AuthService.login`(`AuthService.java:90-`)이 `memberRepository.findByUsername`으로 직접 조회하고 `passwordEncoder.matches`로 비교. Spring Security의 AuthenticationManager를 거치지 않는다.
- 요청 인증: `JwtAuthenticationFilter.java:56-60`이 토큰 claim으로 `AuthenticatedMember`를 만들어 SecurityContext에 직접 설정(DB 조회 없음).
- `httpBasic`/`formLogin`: `src/main` 전체 grep 결과 호출 없음 → 활성화되지 않음.
- `UserDetailsService`/`AuthenticationProvider`/`AuthenticationManager` 빈: 수정 전 프로젝트 코드에 없음(grep). `PasswordEncoder` 빈만 있음(`SecurityConfig.java`의 passwordEncoder).

## 2. 원인 (재현 확인)
- 신규 테스트를 수정 전에 돌려 실패를 재현했다. 로그: `UserDetailsServiceAutoConfiguration : Using generated security password: <UUID>` 와 `Global AuthenticationManager configured with UserDetailsService bean with name inMemoryUserDetailsManager`.
- Boot의 `UserDetailsServiceAutoConfiguration`은 UserDetailsService·AuthenticationProvider·AuthenticationManager 등 관련 빈이 하나도 없을 때만 동작하고, 기본 사용자 `user` + 랜덤 UUID 비밀번호를 만들어 WARN으로 출력한다.

## 3. 선택지
| 안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| A | 항상 `UsernameNotFoundException`을 던지는 `UserDetailsService` 빈 정의 | 자동 구성이 물러나 기본 사용자가 안 생김. 나중에 httpBasic 등을 실수로 켜도 아무도 인증 못 함(fail-closed). `SecurityConfig`는 테스트(@WebMvcTest 등)에서도 로드됨 | 안 쓰는 빈이 하나 생김 |
| B | `@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)` | 코드 1줄 | **이 프로젝트에서는 검증 불가**: `src/test/java/book_log/demo/DemoApplication.java`가 main과 같은 FQCN으로 존재해 테스트 클래스패스에서 main 클래스를 가린다(발견 사항 1). 적용해 보니 테스트 로그에 메시지가 그대로 남았다 |
| C | 로그 레벨만 낮추기 | 없음 | **제외**: 메시지만 숨길 뿐 기본 사용자(`user`)와 임시 비밀번호가 메모리에 그대로 남고, 어떤 경로가 열리면 인증에 쓰일 수 있다. 요구사항에서도 제외 |

**선택: A.** B를 먼저 적용해 봤으나 테스트에서 효과가 확인되지 않아(위 단점) 되돌렸고, 두 환경(운영 main/테스트)에서 같은 코드가 로드되는 `SecurityConfig`에 A를 구현했다. 변경: `SecurityConfig.java`의 `userDetailsService()` 빈.

## 4. 기본 사용자 경로가 열려 있었는지
- 수정 전에도 **HTTP로는 쓸 수 없었다고 판단**: httpBasic/formLogin을 켜지 않았고(grep), 필터 체인은 JWT 필터 + `authenticated()`뿐이며 `/login` 폼 필터도 없다. 기본 사용자는 AuthenticationManager에만 등록되고 이 앱에서 AuthenticationManager를 호출하는 코드가 없다. 단 이 판단은 코드/설정 읽기 기준이며, 수정 전 앱에 `Authorization: Basic user:<임시비번>`을 실제로 보내 보는 확인은 하지 않았다(미확인).
- 이번 수정으로 기본 사용자 자체가 없어졌다(테스트로 확인: `inMemoryUserDetailsManager` 로그가 `userDetailsService`로 바뀜, 로그에 `Using generated` 없음). 별도 조치는 필요 없다.

## 5. 검증 결과 (직접 실행)
- `cd demo && ./gradlew test` → **BUILD SUCCESSFUL**. 결과 XML 합계(직접 집계): tests 351, skipped 9, failures 0, errors 0.
- skipped 9는 `ContentRepositoryPostgresTest`. Docker 데몬이 꺼져 있어(`docker info`의 Server 정보 없음) Postgres 테스트는 실행되지 않았다. 이번 변경과 무관하지만 이 실행에서 Postgres 검증은 하지 않은 것이다.
- 신규 `NoGeneratedPasswordTest`(@SpringBootTest, OutputCapture): 수정 전 실패(메시지 출력), 수정 후 통과. 기존 인증 테스트(`AuthControllerTest`, `JwtSecurityChainTest`, `ProtectedApiAccessTest` 등)는 수정 없이 통과.
- **실제 앱(운영 설정, bootRun) 기동 로그 확인: 미확인(수동 확인 필요).** 위 테스트는 H2 test 프로파일의 실제 컨텍스트 기동 로그를 본 것이다.

### 내가 할 수동 확인
1. 앱을 평소처럼 띄우고(로컬 또는 Cloud Run 새 리비전) 시작 로그에서 `generated security password`, `inMemoryUserDetailsManager`를 검색 → 둘 다 없어야 함.
2. 로그인 → 보호된 API 호출(예: 통계) → refresh → 토큰 없이 호출(401 JSON) 이 이전과 같은지.
3. (선택) `Authorization: Basic base64(user:아무값)`으로 보호된 API 호출 → 401이어야 함.

## 6. 과거 로그에 남은 비밀번호의 노출 위험
- 이 비밀번호는 기동마다 새로 만든 UUID(메모리에만 존재)라 재시작하면 무효다. 그리고 4절대로 앱에서 쓰일 경로가 없었다. 따라서 과거 로그의 값으로 인증될 가능성은 사실상 없다고 판단한다(기동마다 값이 바뀐다는 건 Boot 동작에 대한 이해 + 이번 테스트 로그에서 서로 다른 UUID가 나온 것으로 확인).
- 남는 이슈는 "비밀번호성 문자열이 로그에 있었다"는 위생 문제. 현재 떠 있는 리비전이 수정 전 버전이면 해당 인스턴스 메모리에서만 유효하므로, 배포 후 자연히 사라진다. 과거 로그 삭제/보존 정책은 이번 범위 밖(기록만).

## 7. 발견 사항 (고치지 않음)
1. `demo/src/test/java/book_log/demo/DemoApplication.java`가 `src/main`의 `book_log.demo.DemoApplication`과 같은 패키지·이름이다. 테스트 클래스패스에서 test 쪽이 main을 가려, `@SpringBootTest`는 main의 `@EnableJpaAuditing`·`@EnableConfigurationProperties(ApiConfig.class)`·exclude 등이 없는 클래스로 뜬다(테스트 출력에 main의 "서버가 정상적으로 시작되었습니다!" 대신 "데이터 저장 테스트 시작!"이 나옴). 테스트가 운영 부트스트랩과 다를 수 있으므로 별도 task로 정리 필요(삭제 또는 이름 변경은 기존 테스트 영향 확인 후).
2. 수정 전 앱에 Basic 인증을 실제로 보내 확인하는 검증은 하지 않음(4절).

## 8. 정리 (노션용)
1. **문제 상황**: 앱 시작 로그(Cloud Run 포함)에 `Using generated security password: <UUID>`가 찍혔다. JWT만 쓰는데 Boot가 기본 사용자를 만들고 있었다.
2. **고려한 선택지**: A) 거부 전용 UserDetailsService 빈, B) UserDetailsServiceAutoConfiguration 제외, C) 로그 레벨만 낮춤.
3. **선택 이유**: C는 기본 사용자가 남아 제외. B는 테스트 소스의 중복 DemoApplication이 main을 가려 효과를 검증할 수 없었다. A는 어느 환경에서나 로드되고, 나중에 인증 경로가 실수로 열려도 기본 사용자로는 인증되지 않는 fail-closed다. 트레이드오프: 안 쓰는 빈 하나.
4. **막혔던 부분과 해결**: B를 적용했는데 테스트 로그에 메시지가 그대로 나와 원인을 찾다가, 테스트 디렉터리에 같은 FQCN의 DemoApplication이 main을 가리고 있음을 발견했다. B를 되돌리고 A로 전환, 중복 클래스는 발견 사항으로만 기록했다.

## 9. 복습 질문
(토글 안에 답)
<details><summary>Q1. Spring Boot는 어떤 조건에서 기본 사용자와 임시 비밀번호를 만드나?</summary>

UserDetailsServiceAutoConfiguration이 UserDetailsService·AuthenticationProvider·AuthenticationManager(및 몇몇 resolver/JwtDecoder) 빈이 모두 없을 때만 동작해 `user` + 랜덤 UUID를 만든다. 이 프로젝트는 AuthService가 DB를 직접 조회해서 이런 빈이 없었다. 이번엔 UserDetailsService 빈을 정의해 조건을 깼다.
</details>
<details><summary>Q2. 로그 레벨만 낮추면 왜 안 되나?</summary>

로그만 사라질 뿐 기본 사용자와 임시 비밀번호는 AuthenticationManager에 그대로 등록돼 있다. 나중에 httpBasic 등을 켜면 그 계정이 인증에 쓰일 수 있고, 근본 원인(불필요한 계정 존재)이 남는다.
</details>
<details><summary>Q3. (기존 코드 연결) SecurityConfig의 필터 체인에서 요청이 인증되는 흐름을 설명하고, 그 흐름에서 UserDetailsService가 호출되는 곳이 있는지 말해 보세요.</summary>

요청 → CorsFilter → JwtAuthenticationFilter(:70)가 Bearer access 토큰을 검증해 claim으로 AuthenticatedMember를 SecurityContext에 설정(DB 조회 없음) → authorizeHttpRequests가 permitAll 경로 외에 `authenticated()` 검사 → 없으면 JsonAuthenticationEntryPoint가 401 JSON. 로그인은 `/api/auth/**`(permitAll)에서 AuthService가 `findByUsername` + BCrypt `matches`로 처리해 토큰을 발급한다. 이 흐름에 UserDetailsService는 호출되지 않는다.
</details>
