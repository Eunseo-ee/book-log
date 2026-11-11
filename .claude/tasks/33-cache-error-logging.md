# 33. 캐시 오류 로깅 조사: 스택트레이스(25-i)를 되돌릴지 + 로그에 남는 검색어(캐시 키)

- 번호: 기존 파일 목록의 마지막이 `32-generated-password-log.md`라 `33`. 커밋하지 않음.
- 성격: **읽기 전용 조사**. 코드/설정/테스트/로그 레벨/Redis 설정 수정 없음. `decisions.md`도 수정하지 않음(확정된 결정이 아직 없음. 결정 후 다음 task에서 추가).
- `./gradlew test`: **실행하지 않음**(코드를 바꾸지 않았다). 아래 테스트 설명은 25-i/25-j 문서와 테스트 이름에서 가져온 것이고 이번에 돌려 본 것이 아니다.
- 근거: 이 저장소의 코드·문서 + 로컬 Gradle 캐시의 `spring-context-6.2.1-sources.jar`(실제 소스를 열어 읽음). 운영 로그/Redis/외부 서비스에는 접속하지 않았다.
- 표기: "확인" = 파일/라인으로 읽음, "추정" = 읽은 근거로부터의 추론이나 일반 지식, "미확인" = 확인 수단이 없었음.

## 1. 결론 요약
1. `LoggingCacheErrorHandler(true)`의 `true`는 **스택트레이스 포함**이 맞다(소스 확인). `false`/기본 생성자는 메시지 한 줄만 찍고 **예외 객체를 아예 로그에 넘기지 않는다** → 예외 종류·메시지(WRONGPASS 같은 단서)가 로그에서 통째로 사라진다. 25-i가 켠 이유가 바로 이것.
2. 25-i는 맞게 "임시 진단"이었다. 그리고 원인은 25-j(§배경, 7행)에 "비밀번호 시크릿 불일치, 코드 변경 없음"으로 정리돼 있다. 단 그 로그 자체는 문서에 인용돼 있지 않아 **WRONGPASS가 실제로 스택트레이스에 찍혔는지는 문서로 미확인**이다(25-i의 WRONGPASS는 "일반 지식, 미검증" 해석표 안의 예시).
3. 로그에 **검색어 원문이 그대로** 남는다. 스택트레이스 on/off와 **무관**하게 메시지 본문(`key '<검색어>:<카테고리>:<페이지>'`)에 들어가므로, `false`로 바꿔도 검색어 문제는 해결되지 않는다. 두 체크리스트 항목은 한 손잡이(true/false)로 풀리지 않는다.
4. 검색어는 캐시 핸들러 밖에서도 로그에 남는다(§4). 캐시 키만 가려서는 "검색어가 로그에 없다"가 되지 않는다.
5. 캐시 장애 시 서비스는 DB가 아니라 **외부 API(TMDB)를 직접 호출**해 정상 응답한다(§6). "DB로 fallback"이 아니라 "캐시 미스로 취급"이다.
6. 추천: **한 줄 요약 + 키 마스킹 커스텀 핸들러**(§8의 (c′)). 스택트레이스는 끄되 근본 원인(루트 예외 클래스/메시지)은 한 줄로 남기고, 검색어는 해시/길이로만 남긴다.

## 2. 설정 위치와 true/false의 정확한 의미 (확인)
- 위치: `demo/src/main/java/book_log/demo/config/CacheErrorConfig.java` 21-25행. `CachingConfigurer.errorHandler()`가 `new LoggingCacheErrorHandler(true)`(24행)를 반환. 23행 주석이 임시 진단임을 밝힘.
- 다른 곳에 에러 핸들러는 없다(`RedisCacheConfig`는 CacheManager만; grep `CacheErrorHandler|CachingConfigurer`는 `CacheErrorConfig` 1곳).
- 버전: Spring Boot 3.4.1 → spring-context 6.2.1(25-i §1). 소스 `LoggingCacheErrorHandler.java`를 직접 읽음:
  - 기본 생성자 = `this(false)`, javadoc "does not log stack traces".
  - `logCacheError`(소스 하단): `isWarnEnabled()`일 때 `logStackTraces`가 true면 `logger.warn(message, exception)`, false면 `logger.warn(message)`.
  - 즉 true = **WARN + 예외(원인 사슬 `Caused by:` 전체)**, false = **WARN 메시지 한 줄, 예외 정보 없음**. 레벨은 둘 다 WARN(ERROR 필터에는 안 걸림, 25-i §4-3).
  - 로거 이름 기본값 `org.springframework.cache.interceptor.LoggingCacheErrorHandler`. 생성자 `(String loggerName, boolean)` / `(Log, boolean)`으로 바꿀 수 있다.
- 메시지 형식(소스 확인): `Cache '%s' failed to get entry with key '%s'` / `... put entry ...` / `... evict entry ...` / `Cache '%s' failed to clear entries`.
- `handleCache*Error` 메서드는 public이고 `logCacheError`는 protected → 서브클래스로 메시지/출력 방식을 바꿀 수 있다(§8 (c′)의 근거).

## 3. 캐시 오류 시 실제로 찍히는 것 (확인 + 추정)
| 항목 | 내용 | 근거 |
|---|---|---|
| 레벨/로거 | WARN, `...LoggingCacheErrorHandler` | 소스 |
| 메시지 | `Cache 'tmdbSearch' failed to get entry with key '...'` (put이면 `put`) | 소스 + `TmdbSearchService.java:41` cacheNames |
| 캐시 이름 | `tmdbSearch` 하나뿐(캐시 사용 지점은 `@Cacheable` 1개) | grep: `@Cacheable`은 `TmdbSearchService.java:41` 1건, `@CacheEvict/@CachePut` 없음 |
| 캐시 키 | SpEL `#query + ':' + #category + ':' + #page` → 예: `해리포터:MOVIE:1` | `TmdbSearchService.java:42`. **검색어 원문이 그대로**(정규화·길이 제한·해시 없음). Category 부분은 enum 이름(추정: `toString` 오버라이드 여부는 미확인) |
| 키 앞의 Redis 접두사 | 로그의 키는 SpEL 결과 그대로이고, 실제 Redis 키는 `tmdbSearch::` 접두사가 붙는다 | 추정(spring-data-redis 기본 동작, `RedisCacheConfig.java`에 접두사 설정 없음) |
| 스택트레이스 | `true`라 예외 객체까지 출력. 내용은 예외 종류에 따라 다름(연결/인증/timeout 등) | 소스. 실제 운영 로그는 미확인 |
| 스택 길이 | Spring 캐시 AOP + Redis 템플릿 + Lettuce 프레임이 겹쳐 수십~백 줄 이상일 것 | **추정**(측정 안 함) |
| 호출 수 | 요청당 GET 1회 + (결과가 비어 있지 않을 때만) PUT 1회 | `unless` `TmdbSearchService.java:43`, 25-j §3-1 |

- 검색어 길이 제한은 없다: `SearchController.java:35` `@RequestParam String query`에 `@Size` 등 없음, `SearchService`에도 길이 검증 없음(확인). 길이는 URL 길이 한도에만 걸린다(추정, Tomcat 기본 헤더 한도 8KB). 긴 검색어는 키/로그 한 줄을 그만큼 키운다.
- 키 안에 `'`나 개행이 들어가도 이스케이프 없이 그대로 출력된다(소스의 `String.format` 사용, 이스케이프 없음). 로그 인젝션(한 줄처럼 보이는 가짜 로그) 가능성: **추정**, 이번에 재현하지 않음.

## 4. 사용자 검색어가 로그에 남는 다른 경로 (위치만 기록, 수정 안 함)
| # | 위치 | 내용 | 구분 |
|---|---|---|---|
| 1 | `TmdbSearchService.java:84` | `log.error("TMDB API 호출 중 예외 발생 [query: {}]: {}", query, e.getMessage())` — **검색어를 명시적으로 ERROR 로그에 남김**(TMDB 호출이 실패할 때마다). 이 핸들러와 별개 | 확인 |
| 2 | `KakaoSearchService.java:88` | `e.getMessage()`를 로그. RestTemplate의 I/O 오류 메시지는 요청 URL(쿼리스트링 포함)을 담는다(`I/O error on GET request for "<URL>"`) → 연결/타임아웃 계열 실패 시 검색어가 새는 **추정**(예외 종류에 따라 다름. 4xx/5xx 메시지는 URL을 담지 않음, 추정) | 코드 확인 + 메시지 형식은 추정 |
| 3 | `TmdbSearchService.java:84`의 `e.getMessage()` | 위 2와 같은 이유로 URL 내 검색어가 한 번 더 들어갈 수 있음(1번에서 이미 원문을 찍으므로 중복) | 추정 |
| 4 | `SearchService.java:137` | `log.warn("검색 Provider 실패...", name, e)` — 주석은 "검색어는 남기지 않는다"지만 예외 객체를 통째로 넘겨 **스택트레이스에 예외 메시지(위 2의 URL 포함 가능)가 같이 찍힘**. 캐시 예외도 여기서 잡히지는 않음(캐시 예외는 핸들러가 삼킴) | 확인(코드) + 메시지 내용은 추정 |
| 5 | `GlobalExceptionHandler.java:96` | `e.printStackTrace()` (표준 에러). 알 수 없는 예외의 메시지에 검색어가 있으면 그대로. 검색어를 직접 넣는 코드는 못 찾음 | 확인 / 내용 추정 |
| 6 | 요청 로깅 필터 | `CommonsRequestLoggingFilter` 등 요청 파라미터 로깅 코드 **없음**. `logging.*` 설정, `logback*.xml`도 `src/main`에 **없음**(grep). `application.yml`은 gitignore 대상이라 열지 않음 | 확인(yml 제외) |
| 7 | 플랫폼 로그 | Cloud Run의 요청 로그는 요청 URL(쿼리스트링 포함)을 기록하는 것이 일반적 → `GET /api/search?query=...`로 검색어가 **코드 밖에서** 이미 남을 가능성 | **미확인**(일반 지식, 운영 로그 접속 금지) |
| 8 | 프론트(Next 프록시/Vercel) 로그 | 이번 범위 밖 | 미확인 |
- 결론: 캐시 핸들러만 가려도 1, 7번이 남는다. 개인정보 관점의 "검색어가 로그에 없음"은 이 task의 변경만으로는 달성되지 않는다. 검색어 자체가 민감한 개인정보인지(예: 독서 취향)는 서비스 정책 판단이고, 로그 보존/접근 권한(누가 Cloud Logging을 볼 수 있나)은 미확인.

## 5. 로그 볼륨 위험 (대부분 추정)
- 코드로 확인된 것: 요청 한 번이 캐시 실패를 일으키면 `true`일 때 **스택트레이스 포함 WARN이 최대 2건**(GET, 결과가 있으면 PUT). 로그를 줄이는 장치(중복 제거, 샘플링, 서킷 브레이커, 레이트 리밋)는 없다(25-j §7-5, grep).
- 볼륨은 **트래픽에 비례**한다(증폭은 없음). 개인 프로젝트 수준 트래픽이면 절대량은 작을 가능성이 높지만 실제 트래픽은 미확인.
- 로그 한 건 크기: 스택 수십~백 줄 이상 × 2건/요청(추정). 한 줄 로그 대비 **수십 배** 클 수 있다(추정, 측정 없음).
- Redis가 죽거나 Upstash가 삭제/지연되는 경우:
  - 호스트 소멸/연결 거부: 요청마다 즉시 실패 → 요청당 스택트레이스 2건(추정).
  - 응답 지연/무응답: 요청당 약 1초씩(25-j) 기다린 뒤 같은 로그. 느려진 요청이 스레드를 오래 잡아 쌓일수록 로그도 쌓임(추정, Tomcat 스레드/Cloud Run 동시성 미확인).
  - 결과가 빈 검색은 PUT이 없어 1건.
- Cloud Logging 영향: 수집 용량(GiB) 증가와 비용, 노이즈로 인한 가독성 하락, 다른 중요 로그가 묻힘. 무료 할당량·단가는 **미확인**(확인하려면 콘솔/요금 문서 필요). 멀티라인 스택이 한 엔트리로 묶이는지, 줄별 엔트리로 쪼개지는지는 **미확인**(로깅 형식 설정에 의존, 이 프로젝트는 JSON 로깅 설정 없음: grep).
- 반대로 `false`면 요청당 한 줄(최대 2줄)이라 증폭이 없다. 다만 장애가 지속되는 동안 매 요청 WARN은 여전히 쌓인다(추정).

## 6. 캐시 장애 시 서비스 응답 (fallback) 확인
- 구조(확인): `CacheErrorConfig`가 `CachingConfigurer.errorHandler()`로 예외를 로그만 남기고 삼킴(javadoc 8-15행). Spring `CacheAspectSupport`는 GET 실패를 핸들러에 넘긴 뒤 **캐시 미스로 취급**해 메서드를 실행하고, 결과를 PUT 하다 실패해도 삼킨다(25-j §3-1에서 소스 라인으로 확인된 내용을 인용).
- 따라서 장애 시 동작은 DB 조회가 아니라 `TmdbSearchService.search` 본문 = **TMDB API 직접 호출**이다. 책(Kakao) 검색은 `@Cacheable`이 없어 캐시 장애와 무관(25-h, grep으로 재확인).
- 이 코드 변경(핸들러 교체)이 fallback을 깨지 않는 조건: **핸들러가 예외를 던지지 않을 것**. 커스텀 핸들러를 쓸 때 키가 null이거나 예외 메시지가 null이어도 NPE가 나지 않아야 한다(§9 테스트 항목).
- 기존 테스트(25-i/25-j 문서 기준, **이번에 실행 안 함**): `CacheFailureFallbackTest`, `RedisTimeoutFallbackTest`(4), `RedisTlsTimeoutTest`가 `CacheErrorConfig`를 import해 "캐시 실패에도 검색이 정상 응답하고 외부 API가 호출됨"을 검증한다. 25-i에서 failures 0으로 확인된 기록이 있다. 단 로그 **내용**을 단언하는 테스트는 없다(25-i §5).
- 확정된 방향 "fallback 유지"와 충돌하는 선택지는 없다. 어떤 선택지도 핸들러가 삼키는 구조는 건드리지 않는다.

## 7. 정리해서 볼 두 체크리스트 항목의 관계
| | (1) 스택트레이스 로깅 되돌릴지 | (2) 로그에 검색어(캐시 키) |
|---|---|---|
| 손잡이 | `LoggingCacheErrorHandler(true/false)` | 메시지 본문의 키 |
| true/false로 해결되나 | 예 | **아니오**(둘 다 키를 찍음) |
| 같이 풀려면 | 핸들러를 직접 구성 | 같은 핸들러에서 키를 가공 |
| 핸들러 밖 잔존 | — | §4의 1, 2, 4, 7번 |
- 즉 "(1)을 false로 되돌리고 끝"이면 (2)는 그대로이고, (2)를 풀려면 어차피 핸들러를 만져야 하므로 **둘을 한 번에 같은 핸들러 변경으로 처리하는 것이 자연스럽다.**

## 8. 선택지 비교와 추천 (구현 안 함)
| | (a) true 유지 | (b) false (메시지만) | (c′) 커스텀: 한 줄 요약 + 키 마스킹 | (d) 프로파일별(local true / prod false) |
|---|---|---|---|---|
| 디버깅 용이성 | 최상: 전체 `Caused by` | **최하**: 원인 단서 없음(25-i 이전 상태, WRONGPASS를 못 본 바로 그 상황) | 상: 루트 예외 클래스+메시지 한 줄이면 25-i 때의 인증/연결/TLS/timeout 구분은 대부분 가능(추정). 프레임 단위 추적은 불가 | prod가 최하(= b), local은 상. **원인이 필요한 곳은 prod인데 prod를 끔** |
| 로그 볼륨 | 큼(요청당 스택 2개) | 작음(요청당 1~2줄) | 작음(요청당 1~2줄, 줄이 약간 길다) | prod는 작음 |
| 검색어 노출 | **그대로**(원문) | **그대로**(원문) | 해소(해시/길이만; 원문 없음) | prod도 그대로 |
| 구현 비용 | 0 | 인자 `true` 제거 1줄 | `LoggingCacheErrorHandler` 서브클래스 또는 `CacheErrorHandler` 직접 구현 약 30줄 + 테스트 | 프로파일 분기/설정 추가. local은 Redis가 거의 없어 이득이 작음 |
| 나머지 위험 | 개인정보+비용이 장애 때 가장 커짐 | 다음에 Redis 문제가 나면 또 원인을 못 봄(재배포로 켜야 함) | 코드 한 클래스가 늘고 로그 형식이 우리 것이 됨. 키 해시로는 어떤 검색어인지 식별 불가(디버깅 시 재현이 필요) | prod가 b와 동일한 약점. 환경 차이로 "로컬에서는 보이는데 운영에서는 안 보임" |
| 확정 방향(fallback 유지) | 충족 | 충족 | 충족(핸들러가 던지지 않게만 유지) | 충족 |

**추천: (c′).** 이유:
1. 25-i 문서 자체가 "원인이 안 보이는 것"을 문제로 시작했다. (b)는 그 상태로 돌아가 다음 장애 때 다시 재배포·진단 task가 필요하다. 루트 예외를 한 줄로 남기면 이번 사고(시크릿 불일치)류는 로그에서 바로 분류된다(추정: WRONGPASS 같은 단서는 예외 메시지에 있다고 알려져 있으나 이 프로젝트 로그로 미확인).
2. 검색어 문제는 (a)(b)(d) 어느 것도 못 푼다. (c′)만 같은 변경으로 해결한다.
3. 로그 볼륨은 스택을 끄는 것만으로 대부분 줄고, 해시는 길이를 고정해 긴 검색어/개행도 막는다.
4. 수정 면적이 `CacheErrorConfig` + 새 클래스 1개로 작고 fallback 구조는 그대로다.
- 최소안이 필요하면 (b)로 가되, 그 경우 **(2) 검색어와 원인 가시성 상실은 감수**하는 것이고 `TmdbSearchService.java:84`의 검색어 로그는 별도 처리가 필요하다.
- 스택이 정말 필요할 때를 위한 보험: 서브클래스를 별도 로거 이름(`(String loggerName, boolean)`)으로 두면, 스택트레이스는 **로거 레벨/설정으로 환경변수 하나로 켤 수 있게** 할 수 있다(`LOGGING_LEVEL_...`). 이 방안의 효과는 추정이며 이번 범위(로그 레벨 변경 금지) 밖이라 제안만 한다.
- 키 마스킹 방식 후보(결정은 사용자): ① SHA-256 앞 8자리 + 길이 ② 길이 + 앞 2글자 ③ 카테고리/페이지만(검색어 부분 생략). 같은 검색어를 같은 값으로 묶어 "한 검색어가 계속 실패하는지"를 보려면 ①, 개인정보를 최소로 하려면 ③. (캐시 **키 자체**를 해시로 바꾸는 안은 Redis 키 가독성·기존 항목 무효화(10분 TTL)가 걸려 이 task에서는 제외; 로그에서만 가공.)

## 9. 추천안 적용 시 변경 범위와 테스트 제안 (적용 금지)
변경 범위(다음 task):
| 파일 | 변경 |
|---|---|
| `config/CacheErrorConfig.java` | 반환값을 커스텀 핸들러로 교체, 25-i 임시 주석 제거 |
| `config/` 새 클래스(예: `MaskingCacheErrorHandler`) | get/put/evict/clear 4개 메서드 구현: `Cache '<name>' failed to get entry (key=<마스킹>): <루트 예외 클래스>: <루트 메시지(길이 제한)>`, 예외는 던지지 않음 |
| `decisions.md` | 이 task의 결정 1건 추가(25-i 임시 진단 해소) |
| (선택) `TmdbSearchService.java:84` 등 | 검색어 로그 정리는 **별도 task**로 분리 권장(범위 1기능 규칙) |
- 건드리지 않는 것: timeout/TTL/키 생성 SpEL, 직렬화, Redis 설정, 로그 레벨 설정.

테스트 제안:
1. 핸들러 단위 테스트(`(Log, boolean)`처럼 가짜 `Log`를 주입하거나 OutputCapture): ① 메시지에 **검색어 원문이 없다**(한글/공백/`'`/개행 포함 키로) ② 캐시 이름과 루트 예외 클래스는 있다 ③ `Caused by`/스택 프레임이 없다(한 줄) ④ 로그 길이가 상한 이하(아주 긴 키·긴 예외 메시지).
2. 핸들러가 **던지지 않음**: key가 null, 예외 메시지가 null, 원인 사슬이 순환/없음일 때 NPE·StackOverflow 없이 통과.
3. fallback 회귀: 기존 `CacheFailureFallbackTest`, `RedisTimeoutFallbackTest`, `RedisTlsTimeoutTest`가 수정 없이 통과(get/put 양쪽 실패에도 응답 정상).
4. 통합 로그 확인: 연결 거부(닫힌 포트)에서 WARN 줄 수가 요청당 최대 2줄이고 검색어가 없음(OutputCapture).
5. `./gradlew test`를 실제로 실행해 결과(skipped 포함, 기존 Postgres 9개 skip 여부)를 보고.
6. 배포 후 수동: 같은 검색을 1회 실행하고 Cloud Logging에서 `failed to get entry`가 한 줄이며 원문 검색어가 없는지 확인(Redis를 일부러 끊는 확인은 운영에서 하지 말 것: 별도 판단).

## 10. 발견한 문제 (고치지 않음)
1. `TmdbSearchService.java:84`가 검색어를 ERROR 로그에 직접 남긴다(§4-1). 캐시 로깅 정책과 별도로 정책 결정 필요.
2. `SearchService.java:137` 주석은 "검색어는 남기지 않는다"이지만 예외 객체를 넘겨 예외 메시지(URL 포함 가능)가 남을 수 있다(추정). 주석과 동작이 어긋날 수 있음.
3. 25-i `§6-2`가 "스택트레이스를 켜도 노출 양이 늘진 않는다"고 한 것은 **키 기준으로는 맞지만**, 스택에 담기는 예외 메시지에 호스트/URL 등이 더 들어갈 수 있다는 점은 다루지 않았다(추정; Redis 예외 메시지에 접속 대상이 포함되는지는 미확인). 비밀번호가 메시지에 포함되는지도 미확인(25-i §4-5가 마스킹을 권고만 함).
4. `GlobalExceptionHandler.java:96` `printStackTrace()`는 로거를 거치지 않아 레벨/마스킹 정책이 적용되지 않는다.
5. 검색어에 길이 제한이 없어 캐시 키/Redis 키/로그 한 줄이 길어질 수 있다(`SearchController.java:35`).
6. 25-i 문서의 이 임시 진단이 아직 유지 중이고(25-j §7-4) decisions.md 2026-11-10 항목은 "원인 확인 후 재결정"으로 열려 있다. 이 task의 결정으로 닫을 수 있다.
7. 플랫폼 요청 로그의 쿼리스트링(§4-7)은 코드 밖이라 이번 변경으로는 못 줄이고, 필요하면 검색 API를 GET 쿼리스트링에서 바꿔야 하는 큰 변경이라 별도 판단 사항(미확인).
8. 키를 `:` 구분 문자열 연결로 만들어, `:`를 포함한 검색어가 다른 (query, category, page) 조합과 같은 키가 될 수 있는지는 검토하지 않았다(미확인). 백로그 21행의 키 설계 항목과 함께 볼 후보.

## 11. 사용자가 다음에 할 일
1. 선택지 (a)~(d) 중 하나를 확정(추천 (c′), 마스킹 방식 ①~③ 포함).
2. 확정되면 다음 task(34)에서 §9 범위로 적용, `decisions.md`에 결정 추가.
3. 검색어 로그 정책(§4-1, 2, 4)과 플랫폼 로그(§4-7)를 같이 갈지 별도 task로 나눌지 결정.
4. (선택) Cloud Logging에서 실제 `Cache 'tmdbSearch' failed` 로그 개수·크기를 한 번 조회해 §5의 추정을 실측으로 교체.

## 12. 정리 (노션용)
1. **문제 상황**: Redis 문제를 디버깅하려고 켠 캐시 오류 스택트레이스 로깅(`LoggingCacheErrorHandler(true)`)이 원인 해결 뒤에도 켜져 있다. 장애 때 요청당 스택이 쌓이고, 캐시 키로 사용자 검색어 원문이 로그에 남는다.
2. **고려한 선택지**: (a) true 유지, (b) false로 되돌림, (c′) 한 줄 요약+키 마스킹 커스텀 핸들러, (d) 프로파일별.
3. **선택 이유(추천)**: (b)는 원인 단서가 사라지고 검색어 문제가 그대로이며, (d)는 원인이 필요한 prod를 끈다. (c′)는 스택은 끄되 루트 원인 한 줄을 남기고 검색어를 해시로만 남겨 볼륨·개인정보·디버깅을 모두 만족한다. 트레이드오프: 커스텀 클래스 하나와 테스트가 늘고 해시로는 검색어 식별이 안 된다.
4. **막혔던 부분과 해결**: "스택트레이스 on/off"와 "검색어 노출"이 같은 문제처럼 보였지만, 소스를 읽어 보니 키는 스택과 무관하게 메시지 본문에 찍혀 별개의 손잡이임을 확인했다. 또 검색어는 캐시 핸들러 밖(TMDB 에러 로그, 플랫폼 요청 로그)에도 남는다는 걸 발견해 범위를 분리했다.

## 13. 복습 질문

<details>
<summary>Q1. `LoggingCacheErrorHandler`가 캐시 예외를 삼키고 로그만 남기는 이유는 무엇이고, 이 프로젝트에서 `true`와 `false`는 각각 로그에 무엇을 남기나?</summary>

답:

</details>

<details>
<summary>Q2. 스택트레이스를 항상 켜 두는 것과 끄는 것의 트레이드오프를 로그 볼륨, 디버깅, 개인정보(검색어) 세 관점에서 설명해 보세요. 스택트레이스를 끄면 검색어 노출 문제가 해결되나요?</summary>

답:

</details>

<details>
<summary>Q3. (기존 코드 연결) `TmdbSearchService`의 `@Cacheable` 키 `#query + ':' + #category + ':' + #page`는 왜 세 값이 모두 들어가야 하고, Redis가 죽었을 때 이 메서드는 어떻게 동작하나요?</summary>

답:

</details>
