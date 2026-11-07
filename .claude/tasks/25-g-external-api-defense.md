# 25-g. 외부 API 방어 (Kakao 예외 처리 + searchAll provider 격리 + URL 인코딩 + Redis timeout)

- 번호: 25-f(배포)는 아직 안 했고 요청서가 25-g로 지정. 선례(`25-a`~`25-e`)의 `25-g-이름.md` 형태를 따랐다. 커밋하지 않음.
- 목표: 배포 전에 Kakao/TMDB API나 Redis가 느리거나 죽어도 서비스 전체가 같이 죽지 않게 한다.
- 범위 밖: 캐시 로직/키 변경, 배포 설정(25-f), searchAll 병렬화, Swagger/actuator/CORS. 발견한 다른 문제는 아래 "발견한 문제"에만 기록.

## 현황 (코드 수정 전에 읽고 확인한 것)
- **Kakao 실패 시**: `KakaoSearchService.search`에 try/catch가 **없었다**. 4xx/5xx(`HttpStatusCodeException`), 타임아웃/연결 실패(`ResourceAccessException`), JSON 파싱 실패(`RestClientException`), 빈 본문·`meta` 없음(`body.getMeta()` NPE)이 모두 그대로 던져져 `SearchService` → `SearchController` → `GlobalExceptionHandler`의 `Exception` catch-all에서 **500**(+`printStackTrace`)이 된다.
- **TMDB**: 이미 처리가 있다. `catch (Exception)` → 로그(`log.error`, 메시지+검색어) → 빈 `SearchResponse`(results 빈 리스트, totalPages 1, totalResults 0)를 반환. 캐시(`@Cacheable`)는 `unless`로 빈 결과를 제외해 장애 결과가 고정되지 않게 해 둠. → Kakao를 **같은 패턴**으로 맞췄다.
- **AniList**: `AnimeSearchService`는 `@Service`가 주석 처리되고 클래스 전체가 주석이라 provider가 아니다(decisions 2026-10-19). 이번 범위에서 제외. (주석 안의 반환 타입도 `List`라 이미 `SearchProvider`와 안 맞음 — 활성화할 때 손봐야 함.)
- **searchAll**: `providers.stream().map(p -> p.search(ALL, ...))`로 **순차** 호출, `supports()` 확인 없음, provider별 격리 **없음**. 어느 provider든 예외를 던지면 전체가 실패. 그리고 Kakao가 던졌으니 통합 검색은 **TMDB 결과가 멀쩡해도 500**이었다. `r.getResults()`가 null이면 NPE.
- **URL**: Kakao와 TMDB 모두 `"...?query=" + query` 문자열 연결 + `exchange(String url, ...)`. 변경 전 베이스라인 테스트(진짜 `RestTemplate` + `MockRestServiceServer`로 실제 나가는 URI를 확인)에서: 한글, 공백, `%`(`100%`, `%EC%95%88` 같은 문자열 포함)는 **정상**(RestTemplate이 String URL을 한 번 인코딩, 이중 인코딩 아님). **`&`, `#`, `+`는 깨짐**: `a&b`는 파라미터가 둘로 갈라지고, `c#sharp`는 `#` 뒤가 fragment로 잘려 서버에 안 가고, `a+b`는 서버가 공백으로 읽는다(18개 중 6개 실패로 확인).
- **Redis**: Lettuce(`spring-boot-starter-data-redis`). `application.yml`에 host/port/ssl만 있고 **timeout 설정은 어디에도 없었다**(소스·yml·properties grep). 이 경우 Spring Data Redis의 Lettuce 기본값(명령 60초, 연결 10초 — 라이브러리 기본값으로 알고 있음, 값을 코드에서 직접 찍어 보지는 않음)이 적용된다. **실행으로 확인한 것**: timeout을 비운 상태에서 "연결만 받고 응답 없는 서버" 테스트가 15초 상한을 넘겨 실패했다. 즉 Redis가 멈추면 요청 스레드가 기다리는 상태가 된다.
- **Redis 장애 시 동작(변경 전)**: `CacheErrorConfig`의 `LoggingCacheErrorHandler`가 캐시 get/put 예외를 삼키고 로그만 남기는 **fail-open이 이미 있었다**(`CacheFailureFallbackTest`가 "연결 거부"만 검증). 빠져 있던 것은 "무응답" 대비 timeout이다. Redis를 캐시 이상으로 쓰는 곳은 없다: `@Cacheable`은 `TmdbSearchService.search` 하나뿐이고 `RedisTemplate` 직접 사용은 없다(grep). → 요구사항 5의 "예외 상황"은 없음.
- **RestTemplate**: `ApiConfig`에 connect 3초 / read 3초가 이미 있다. 이번에 안 건드림.

## 변경 내용
| 파일 | 내용 |
|---|---|
| `service/KakaoSearchService.java` | try/catch(Exception) → TMDB와 같은 빈 응답. `UriComponentsBuilder`로 URI 생성. 로그는 예외 종류+메시지만(헤더/키 없음) |
| `service/TmdbSearchService.java` | URL 생성만 `UriComponentsBuilder`로 변경 (예외 처리는 기존 그대로) |
| `service/SearchService.java` | `searchAll`이 provider마다 `searchIsolated`를 호출. 예외/null 응답은 WARN 로그(provider 이름 + 원인 예외) 후 빈 응답으로 대체 |
| `resources/ops-defaults.properties` | `spring.data.redis.timeout=1s`, `spring.data.redis.connect-timeout=1s` |
| 기존 테스트 4개(`CacheFailureFallbackTest`, `KakaoSearchServiceTest`, `TmdbSearchServiceTest`, `TmdbSearchServiceCacheTest`) | mock 매칭을 `exchange(anyString(), ...)` → `exchange(any(URI.class), ...)`로만 변경 (production이 String 대신 URI를 넘기게 되어서). 검증 내용은 그대로 |
| 신규 테스트 4개 | `ExternalApiUrlEncodingTest`, `KakaoSearchServiceFailureTest`, `SearchServiceIsolationTest`, `RedisTimeoutFallbackTest` |

`UnifiedSearchResponse`/`SearchResponse` 구조는 **바꾸지 않았다**. `HighlightRepositoryTest`, application.yml, application-api-key.yml, Dockerfile, SecurityConfig는 건드리지 않았다.

## 결정 사항과 이유
1. **Kakao는 TMDB 패턴 그대로**(catch → 빈 응답 + 로그). 새 예외 타입이나 502/503 매핑을 만들지 않았다. 이유: 이미 TMDB가 그렇게 동작하고(`TmdbSearchServiceTest.timeoutHandlingTest` 등이 고정), 단일 카테고리 검색(BOOK)과 통합 검색의 실패 동작이 달라지면 프론트가 더 혼란스럽다.
2. **격리는 provider 안 catch + searchAll의 `searchIsolated`로 이중**. Tmdb/Kakao는 스스로 삼키지만, 앞으로 추가될 provider나 예상 못 한 예외(캐시 프록시 계층 포함)가 통합 검색을 죽이지 않게 하는 마지막 방어선이다. 순차 호출은 그대로 둠(격리에 병렬화가 필요하지 않다).
3. **전부 실패했을 때 = 200 + 빈 결과(totalPages 1, totalResults 0)**. 근거: 기존 규약이 "TMDB 단독 실패 → 빈 200"이고 `GlobalExceptionHandler`에도 외부 API 장애용 상태 코드가 없다. 같은 규약을 따랐다. 트레이드오프: 프론트가 "결과 없음"과 "전부 장애"를 구분하지 못한다. 구분하려면 응답에 필드를 추가(DTO 변경)하거나 502를 내려야 하는데 이번 범위(DTO 가급적 유지)를 넘어 하지 않았고, 발견한 문제 1번에 남겼다.
4. **URL은 `UriComponentsBuilder...queryParam("query", "{query}").encode().buildAndExpand(query).toUri()`** 후 `URI` 객체를 `exchange`에 전달. `.encode()`가 URI 변수 **값**을 엄격하게(`& # + 공백` 포함) 인코딩하고, `URI`를 넘기면 RestTemplate이 다시 인코딩하지 않는다(이중 인코딩 없음). `RestTemplate`의 `UriTemplateHandler`를 전역 변경하는 안은 모든 호출에 영향을 줘서 채택하지 않았다. 트레이드오프: 기존 테스트 mock 매칭 타입 변경이 필요했다.
5. **Redis timeout 기본값 1초/1초, 환경변수로 덮어쓰기**. 위치는 25-e와 같은 이유로 `ops-defaults.properties`(application*.yml은 이미지에 안 들어감, @PropertySource라 환경변수가 덮어씀). 환경변수 이름은 25-a의 Spring relaxed binding 방식 그대로 `SPRING_DATA_REDIS_TIMEOUT`, `SPRING_DATA_REDIS_CONNECTTIMEOUT`(테스트에서 `spring.data.redis.timeout=250ms` 같은 프로퍼티 덮어쓰기는 확인했고, 실제 환경변수 이름 바인딩은 25-a에서 확인한 규칙에 의존하며 이 task에서 따로 검증하지 않았다). 값 선택 근거: 같은 리전의 Redis 응답은 보통 수 ms~수십 ms라 1초면 정상 지연은 넘기고 무한 대기는 막는다. 트레이드오프: Redis가 멈추면 요청 하나가 get과 put에서 **각각** 최대 1초씩, 최대 약 2초 느려진다(실측: 무응답 서버 테스트 전체 약 2.5초). 더 줄이면(예: 300ms) 정상적인 느린 응답도 캐시 미스가 된다. Upstash(원격, TLS)의 실제 RTT는 측정하지 않았으니 25-h에서 확인하고 필요하면 환경변수로 조정.
6. **Redis fail-open은 이미 있어서 새 코드를 만들지 않음**(`CacheErrorConfig`). 이번에는 그것이 "무응답"에서도 지켜지게 timeout만 추가하고, 연결 거부/무응답 두 경우를 테스트로 고정했다.

## 막힌 부분과 해결
- Redis 테스트에서 `ApiConfig`를 mock으로 두니 `@ConfigurationProperties`+`@NotBlank` 바인딩 검증에서 컨텍스트가 실패(Boot 자동 구성이 들어오면 mock 인스턴스도 바인딩 대상). 실제 `ApiConfig` + 가짜 `api.*` 속성으로 해결.
- `TmdbSearchService`가 `@Cacheable` 프록시(JDK 인터페이스 프록시)라 클래스 타입으로 `getBean`하면 못 찾는다 → `SearchProvider`로 조회.
- 첫 Redis 테스트가 약 12초 걸려 flaky를 의심했으나, 호출 자체는 첫 1.9초/두 번째 29ms이고 나머지는 컨텍스트 기동 시간이었다. 테스트의 호출별 상한(15초)에는 영향 없음.
- 중간에 쉘 heredoc 인용 오류로 테스트 파일 생성이 한 번 실패 → 파일 쓰기 도구로 다시 작성.

## 테스트 (신규)
- `ExternalApiUrlEncodingTest` (18): 한글+공백, `&`, `#`, `%`, 이미 인코딩처럼 보이는 `%EC%95%88`, `+`, `= ? /`, 앞뒤 공백, 이모지 × Kakao/TMDB. 실제 나가는 URI에서 query 파라미터가 정확히 1개, 한 번만 디코딩하면 원문과 같고, 값 안에 `+ & # 공백`이 날것으로 없는지, TMDB는 language/page 유지 확인.
- `KakaoSearchServiceFailureTest` (12): 401/400/429/500/503, 읽기 타임아웃, 연결 실패, JSON 아님, 타입이 틀린 JSON, 빈 본문, meta 없음 → 모두 빈 응답(totalPages 1) + ERROR 로그 + **로그(스택트레이스 포함)에 키·`KakaoAK` 없음**. 정상 응답 회귀 1개.
- `SearchServiceIsolationTest` (6): 한 provider 예외(앞/뒤 순서), WARN 로그에 원인 예외 보존, null 응답, 전부 실패 → 빈 200 형태, 전부 성공 시 기존 병합·정렬 유지.
- `RedisTimeoutFallbackTest` (4): 기본 timeout 1초/1초 적용(Boot `RedisAutoConfiguration`으로 운영과 같은 경로), 프로퍼티로 덮어쓰기, a) 연결 거부(포트에 아무도 없음) b) 연결만 accept하고 응답 안 하는 `ServerSocket` → 15초 상한 안에, 실제로는 8초 미만에 끝나고 외부 API 결과를 반환. 클래스 `@Timeout(60)`과 호출별 `assertTimeoutPreemptively(15초)`로 테스트 자체의 상한도 둠.
- **실패 확인(테스트가 헛돌지 않는지)**: 구현 전 URL 테스트는 6개 실패, timeout 설정을 잠깐 지운 상태에서 Redis 무응답 테스트와 기본값 테스트는 실패한 것을 확인하고 원복했다.
- 실제 외부 API/Redis에 의존하는 테스트 없음 (`MockRestServiceServer`, mock, 로컬 `ServerSocket`).

## 검증 결과 (실행한 것만)
- `./gradlew cleanTest test`(demo/): 마지막 전체 실행은 파일 맨 아래 "최종 테스트 결과"에 기록.
- 실행하지 않은 것: 실제 Kakao/TMDB/Upstash 호출, TLS(ssl=true) 연결의 무응답 상황(테스트는 ssl 꺼짐), `application.yml`이 없는 환경(CI)에서의 신규 테스트(CI 결과는 push 후 확인 필요), Docker 이미지에 `ops-defaults.properties`가 들어가는지.

## 발견한 문제 (고치지 않음)
1. **프론트가 "결과 없음"과 "외부 API 장애"를 구분할 수 없다.** 전부 실패해도 200 + 빈 목록. 구분이 필요하면 응답에 `partial/failedProviders` 같은 필드를 추가하거나 장애용 상태 코드를 정해야 한다(DTO/에러 규약 변경이라 별도 task 후보).
2. **`KakaoSearchService`는 `page`를 쓰지 않는다**(URL에 page 파라미터가 없음) — 페이지를 넘겨도 항상 첫 페이지. 또 `totalPages`를 20으로 나누는데 Kakao 기본 `size`는 10으로 알고 있어(이번에 확인하지 않음) 페이지 수가 실제와 다를 수 있다.
3. **최악 지연**: ALL 검색은 순차 호출이고 RestTemplate이 connect 3초+read 3초라, 두 provider가 모두 멈추면 요청 하나가 최대 약 12초(+Redis 2초) 걸릴 수 있다. 격리는 됐지만 느린 건 그대로다. 병렬화나 timeout 단축은 범위 밖이라 안 했다. 25-h 부하/장애 확인 후보.
4. **로그에 검색어가 남는다.** TMDB(`log.error`에 query)와 Kakao/`RestTemplate` 예외 메시지(`I/O error on GET request for "https://.../?query=..."`)에 사용자가 입력한 검색어가 포함된다. API 키는 URL이 아니라 헤더라 메시지에 없다(테스트한 실패 유형 기준, 모든 예외 유형을 증명한 것은 아님). 개인정보 관점에서 검색어를 남길지 25-h에서 결정 필요.
5. `SearchService`의 단일 카테고리 경로(BOOK/MOVIE/ANIME)는 provider 안의 catch에만 의존하고 `searchIsolated`로 감싸지 않았다(provider가 이미 삼키므로 실익이 작고, 한 provider만 부르는 경로라 "격리"할 대상이 없음).
6. `GlobalExceptionHandler`의 catch-all이 `printStackTrace`를 쓴다(25-e 발견 3·5와 같은 뿌리, 이월).
7. `AnimeSearchService`(AniList)는 전체가 주석이고 반환 타입이 이미 `SearchProvider`와 달라 활성화하려면 이번 패턴(try/catch, URI 빌더)을 적용해 다시 써야 한다.
8. RedisCacheConfig 값 직렬화(`GenericJackson2JsonRedisSerializer`)·캐시 키 설계는 범위 밖이라 손대지 않음.

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: Kakao 호출에 예외 처리가 없어 Kakao 하나가 실패하면 통합 검색이 TMDB 결과가 있어도 500이 됐다. 검색어를 문자열로 이어 붙여 `&`, `#`, `+`가 들어가면 잘못된 요청이 나갔다. Redis는 timeout 설정이 없어 멈추면 요청 스레드가 오래 매달릴 수 있었다.
2. **고려한 선택지**: (실패 처리) ① 예외를 던지고 컨트롤러에서 502 ② provider 단위로 삼키고 빈 응답(채택, 기존 TMDB 패턴). (인코딩) ① 문자열을 직접 `URLEncoder`로 인코딩 ② RestTemplate 전역 `UriTemplateHandler` 변경 ③ `UriComponentsBuilder.encode()`로 URI 객체 생성(채택). (Redis) ① 캐시 예외만 삼키기(이미 있음) ② + timeout 명시(채택).
3. **선택 이유**: 외부 API는 통제할 수 없으니 실패를 provider 단위로 가두는 게 핵심이고, 새 패턴보다 기존 TMDB 패턴과 일관되는 편이 유지보수에 유리하다. `URLEncoder`는 공백을 `+`로 바꿔 RestTemplate이 또 인코딩하는 이중 인코딩 위험이 있고 전역 핸들러 변경은 영향이 넓다. Redis는 캐시라서 가용성보다 응답성이 우선이고 가장 위험한 건 무한 대기다. 트레이드오프: 장애와 "결과 없음"이 구분되지 않고, Redis 장애 시 요청이 최대 약 2초 느려진다.
4. **막혔던 부분과 해결**: 위 "막힌 부분과 해결" 참고.

## 복습 질문
<details><summary>Q1. 통합 검색(searchAll)에서 provider 하나가 실패해도 나머지가 반환되게 한 방식은? provider 안에서도 catch하는데 searchAll에서 또 격리하는 이유는? 전부 실패했을 때 200 + 빈 결과로 한 근거와 단점은?</summary>

(여기에 답)

</details>

<details><summary>Q2. Redis는 이미 캐시 예외를 삼키는 설정(CacheErrorConfig)이 있었는데 왜 timeout을 따로 설정해야 했나? "연결 거부"와 "연결은 되지만 응답 없음"은 timeout 관점에서 어떻게 다르고, 기본값 1초의 트레이드오프(요청 하나가 최대 얼마나 느려질 수 있나)는?</summary>

(여기에 답)

</details>

<details><summary>Q3. (기존 코드 연결, 25-a/25-e/TMDB 캐시) Redis timeout 기본값을 application.yml이 아니라 ops-defaults.properties에 둔 이유는? 운영에서 이 값을 바꾸려면 어떤 환경변수를 쓰며(25-a 방식), 그 값이 왜 우선하나? 또 TMDB 캐시의 `unless`(빈 결과 캐시 제외)는 이번에 Kakao처럼 실패를 빈 응답으로 바꾸는 방식과 어떻게 맞물리나?</summary>

(여기에 답)

</details>

## 최종 테스트 결과
`./gradlew cleanTest test`(demo/, 모든 변경 후 마지막 실행): **BUILD SUCCESSFUL, 327개 실행, 실패 0, 에러 0, skipped 9**. 이전 287개 + 신규 40개(URL 18 + Kakao 실패 12 + 격리 6 + Redis 4). skipped 9는 이전과 같은 `ContentRepositoryPostgresTest`(Testcontainers가 Docker를 인식 못 함, 통과 미확인 상태 그대로). 커밋 안 함.
