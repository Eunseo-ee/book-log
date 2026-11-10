# 25-i. 캐시 오류 로그에 스택트레이스 켜기 (임시 진단)

- 번호: 직전이 `25-h-redis-cache-investigation.md`라 `25-i`로 이었다. 커밋/푸시하지 않음.
- 배경: prod에서 `LoggingCacheErrorHandler`의 WARN(`Cache 'tmdbSearch' failed to get/put entry with key ...`)이 반복되고 Upstash Commands는 0. 환경변수(host/port/ssl/password)는 사용자가 확인했고 정상. 그런데 기본 설정은 스택트레이스를 남기지 않아 근본 원인(인증/연결/TLS/timeout)이 로그에 안 보인다.
- 목표: 캐시 오류를 삼키는 동작(fail-open)은 그대로 두고, 로그에 스택트레이스(`Caused by:`)만 추가한다. **원인 확인용 임시 진단**이다.

## 1. 확인한 것 (근거)
- 등록 위치: `demo/src/main/java/book_log/demo/config/CacheErrorConfig.java`, `errorHandler()` 메서드(변경 전 `return new LoggingCacheErrorHandler();`). `RedisCacheConfig`에는 에러 핸들러가 없고 CacheManager만 있다.
- 변경 전 생성자: 인자 없는 기본 생성자 → `logStackTrace=false`.
- Spring 버전: `demo/build.gradle:3`의 Spring Boot `3.4.1` → Spring Framework `6.2.1`. 로컬 Gradle 캐시의 `spring-context-6.2.1.jar`에서 `LoggingCacheErrorHandler.class`를 열어 생성자 시그니처를 확인했다: `()`, `(boolean)`, `(String, boolean)`, `(Log, boolean)`. 즉 `new LoggingCacheErrorHandler(true)`로 스택트레이스를 켠다. 같은 클래스 파일에서 메시지 문자열도 확인했다: `Cache '%s' failed to get entry with key '%s'`, `... failed to put entry ...`, `... failed to evict entry ...`, `Cache '%s' failed to clear entries` (25-h의 "기억에 의존한 추정"이 이 부분은 확인됨으로 바뀜). 레벨(WARN)과 `logStackTrace`가 true일 때 예외 객체를 로거에 넘기는 동작은 클래스 파일의 생성자/문자열로는 확인했지만 바이트코드 흐름을 끝까지 따라가지는 않았다. 그래서 아래 테스트 로그로 보강했다(5번).

## 2. 변경 (최소)
| 파일 | 변경 |
|---|---|
| `config/CacheErrorConfig.java` | `new LoggingCacheErrorHandler()` → `new LoggingCacheErrorHandler(true)` + 임시 진단 주석 1줄 |

- 변경 후 위치: `CacheErrorConfig.errorHandler()` 반환문. 예외를 삼키는 구조(핸들러가 던지지 않음)는 그대로다.
- 건드리지 않은 것: 에러 핸들러 제거/전파 방식 변경, timeout(1s), connect-timeout, TTL(10분), 캐시 키, 직렬화, application*.yml, Dockerfile, .dockerignore, CI, 배포 설정.

## 3. 되돌리는 방법
`CacheErrorConfig.errorHandler()`에서 `new LoggingCacheErrorHandler(true)`를 `new LoggingCacheErrorHandler()`로 바꾸고 주석 1줄을 지운다. 또는 이 변경만 `git revert` 한다(커밋 후라면). 유지할지 되돌릴지는 원인 확인 후 다시 결정한다(decisions.md 2026-11-10 항목).

## 4. 배포 후 사용자가 확인할 절차 (문서만, 코드 실행 아님)
1. 이 변경을 배포한다(이 task는 배포하지 않음).
2. 배포된 앱에서 **같은 TMDB 검색(영화/드라마)을 한 번** 실행한다.
3. Cloud Logging에서 `Cache 'tmdbSearch' failed` 또는 `LoggingCacheErrorHandler`로 검색한다. 이제 한 줄이 아니라 스택트레이스가 붙은 로그가 보여야 한다. (severity는 WARNING이다. ERROR 필터를 걸면 안 보인다.)
4. 스택트레이스에서 **`Caused by:` 줄을 맨 아래부터** 찾아 예외 클래스와 메시지를 확인한다(맨 아래가 근본 원인).
5. 메시지에 비밀번호/키가 있으면 마스킹하고 전달한다. 호스트명도 외부에 공유할 때는 가린다. 검색어는 키(`key '...'`)에 들어가므로 같이 가린다.
6. get과 put 두 줄 모두 같은 예외인지 본다(get이 실패하면 put도 보통 같은 이유로 실패한다).

### 예외 종류별 해석표 — **일반 지식, 미검증** (이 저장소 코드 근거가 아니며 실제 prod 로그로 확인된 바 없음)
| 로그에 보이는 것 | 의미(일반적 해석) | 다음 확인 |
|---|---|---|
| `WRONGPASS` / `NOAUTH` / `invalid username-password pair` | 인증 문제. 비밀번호 틀림, 비밀번호 없이 접속, 사용자명 불일치 | 시크릿 버전, ACL 사용자명(`SPRING_DATA_REDIS_USERNAME` 필요 여부) |
| `RedisConnectionException` / `Unable to connect to ...` / `Connection refused` | 연결 실패. 호스트/포트 오류, `localhost`로 접속(host가 안 읽힘), 네트워크 차단 | 로그에 찍힌 접속 대상이 의도한 호스트인지(마스킹하고 비교), egress 설정 |
| `SSLHandshakeException` / `SSLException` / `unable to find valid certification path` / `Connection reset` / `Connection closed` 직후 | TLS 문제. ssl 설정 불일치, 인증서 신뢰 문제 | `ssl.enabled` 실제 반영 여부, JVM 신뢰 저장소 |
| `RedisCommandTimeoutException` / `Command timed out after ...` / `QueryTimeoutException` | 명령 timeout. 현재 `timeout=1s` 안에 응답 못 받음. 접속은 됐으나 느리거나 연결 직후 막힘 | RTT 측정, 리전, 그때 timeout을 조정할지 판단 |
| `RedisConnectionFailureException: Unable to connect to Redis` 안에 위 중 하나가 `Caused by`로 있음 | Spring이 감싼 예외일 뿐이다. **가장 아래 Caused by**를 본다 | 같은 표로 재분류 |
| `SerializationException` / `JsonMappingException` / `InvalidDefinitionException` | 연결은 됐고 직렬화/역직렬화 문제 | 단, 이 경우 Upstash Commands가 0이 아니어야 한다. 현재 증상과 어긋남 |
| `IllegalStateException: ... closed` / `LettuceConnectionFactory ... STOPPED` | 팩토리가 정상 기동하지 않음 | 기동 로그 확인 |

## 5. 테스트 (실행한 그대로)
- 실행: `demo/`에서 `./gradlew test` → **BUILD SUCCESSFUL** (43s).
- 결과 XML 합계: **334 tests, 0 failures, 0 errors, 9 skipped.** skipped 9개의 이유는 이번에 확인하지 않았다(Testcontainers가 Docker 없으면 skip하는 구조라는 task 20 기록이 있으나, 어떤 테스트인지는 조회하지 않았다).
- 캐시 오류 시 검색이 정상 응답하는 기존 테스트: 있음. `CacheFailureFallbackTest`(1개), `RedisTimeoutFallbackTest`(4개), `RedisTlsTimeoutTest`(1개) 모두 failures 0, errors 0, skipped 0. 세 테스트 모두 `CacheErrorConfig`를 import하므로 변경된 핸들러로 실행됐다. `RedisTimeoutFallbackTest` 결과 XML에 `failed to get entry` 로그가 3건 있어 실제로 핸들러가 호출되어 삼키는 경로가 돌았음을 확인했다.
- 새 테스트는 추가하지 않았다(요청). 한계: **스택트레이스가 실제 로그에 `Caused by:`로 찍히는지를 단언하는 테스트는 없다.** 이번 확인은 생성자 시그니처와 위 테스트들의 통과까지이고, 로그 출력 형태는 배포 후 4번 절차로 확인해야 한다.

## 6. 발견한 문제 (고치지 않음)
1. 책(Kakao) 검색은 캐시를 전혀 쓰지 않는다(`@Cacheable` 없음). 25-h에서 기록.
2. 캐시 키와 이 로그(`key '...'`)에 **검색어 원문**이 들어간다. 스택트레이스를 켜면 같은 로그가 더 길어지고 키는 그대로 출력되므로 노출 양이 늘진 않지만 로그가 커진다. 키/로그 마스킹 정책은 별도 결정 필요(25-g 발견 4, 25-h 발견 3).
3. 요청마다 get과 put 두 번 모두 실패하면 **스택트레이스가 요청당 2개씩** 쌓인다. Redis가 계속 죽어 있는 동안 트래픽에 비례해 로그가 폭증한다(비용/가독성). 진단 후 유지 여부 판단 근거가 된다.
4. Redis 연결 실패가 ERROR가 아닌 WARN이고 readiness에서 Redis가 빠져 있어 설정 오류가 조용히 숨는다는 구조적 문제는 25-h 발견 1, 2번과 같다.
5. 생성자 `(Log, boolean)`/`(String, boolean)` 오버로드가 있어 로거 이름을 분리할 수 있다. 필요하면 캐시 오류만 별도 로거로 레벨을 조절할 수 있으나 이번 범위 밖이라 하지 않았다.

## 7. 정리

### 변경 파일과 요약
- `config/CacheErrorConfig.java`: 에러 핸들러 생성자에 `true` 추가(스택트레이스 로깅). 주석 1줄. 테스트/설정 변경 없음.
- `.claude/decisions.md`: 임시 진단 결정 1건 추가.
- 이 task 파일 신규.

### 사용자가 다음에 할 일
1. 변경을 확인하고 커밋/배포 여부 결정(이 task는 커밋·배포하지 않음).
2. 배포 후 4번 절차대로 같은 검색 1회 실행 → `Caused by:` 맨 아래 예외를 마스킹해서 알려 주기.
3. 원인 확정 후: 설정 수정 task, 그리고 스택트레이스 로깅을 유지할지 되돌릴지 결정(3번 방법).

### 트러블슈팅 정리 (면접/노션용)
1. **문제 상황**: Redis 캐시가 동작하지 않는데 로그에는 원인 없이 "failed to get entry"만 있었다.
2. **고려한 선택지**: ① 핸들러 제거해 예외 전파(원인은 보이지만 검색이 실패) ② 핸들러 유지 + 스택트레이스 켜기(채택) ③ 별도 Redis 연결 테스트 코드 작성(범위 확장, 외부 접속 필요).
3. **선택 이유**: 캐시는 성능 최적화일 뿐 필수 의존성이 아니므로 fail-open을 유지해야 한다. 스프링이 제공하는 `logStackTrace` 옵션만 켜면 변경이 1줄이고 쉽게 되돌릴 수 있다. 트레이드오프: 장애가 계속되면 요청당 스택트레이스가 쌓여 로그가 커진다.
4. **막혔던 부분과 해결**: 사용 중인 Spring 버전(6.2.1)의 생성자를 문서 기억이 아니라 jar의 클래스 파일에서 직접 확인했다. JDK 도구(`javap`)가 PATH에 없어 파이썬으로 클래스 파일 문자열을 읽었다.

## 8. 복습 질문

<details>
<summary>Q1. 캐시(Redis) 오류를 예외로 전파하지 않고 삼키는 설계가 왜 필요한가? 반대로 삼키는 것의 단점은 이번 증상에서 어떻게 나타났는가?</summary>

답:

</details>

<details>
<summary>Q2. 스택트레이스 로깅을 항상 켜둘 때의 비용은 무엇인가? (로그 양, 비용, 노출 정보 관점으로)</summary>

답:

</details>

<details>
<summary>Q3. 이번 증상에서 Upstash Commands가 0이라는 것은 무엇을 의미하는가?</summary>

답:

</details>
