# 25-h. prod(Cloud Run) Redis 캐시 미동작 조사 (코드 기준)

- 번호: 직전이 `25-g-external-api-defense.md`, 25-f가 "Redis 캐시 사용 확인은 25-h"로 넘겨 둔 항목(§11)이라 `25-h`로 이었다. 커밋하지 않음.
- 성격: **조사만**. 코드/설정/YAML/테스트 수정, 배포, 커밋, 외부 서비스(Upstash, GCP) 접속 없음. 비밀값은 읽지도 출력하지도 않았다(`application.yml`은 키 이름과 비밀이 아닌 값만 마스킹해서 확인, `application-api-key.yml`은 키 이름만).
- `./gradlew test`: **실행하지 않음**. 코드를 바꾸지 않았기 때문이다.
- 배경: prod에서 영화/드라마(TMDB) 검색 후 Upstash Data Browser에서 `*` 조회 시 키 없음. Cloud Run 로그에 ERROR 없음. Upstash 사용량 Commands 0, Bandwidth 약 90B.

## 1. 캐시 구성 (코드로 확인)

| 항목 | 값 | 위치 |
|---|---|---|
| `@EnableCaching` | `RedisCacheConfig` 한 곳 | `config/RedisCacheConfig.java:16` |
| CacheManager | `RedisCacheManager` 빈을 **직접 생성** (`cacheDefaults`만, 캐시별 개별 설정 없음) | 같은 파일 `:20-34` |
| TTL | 10분 (`entryTtl(Duration.ofMinutes(10))`) | `:25` |
| null 캐싱 | 안 함 (`disableCachingNullValues`) | `:26` |
| 캐시 이름 | `tmdbSearch` 하나뿐 | `TmdbSearchService.java:41` |
| 키 직렬화 | `StringRedisSerializer` | `:27-28` |
| 값 직렬화 | `GenericJackson2JsonRedisSerializer` (JSON + `@class` 타입 정보) | `:29-30` |
| 키 접두사 | 설정 없음 → Spring 기본 `{캐시이름}::` 이므로 실제 Redis 키는 `tmdbSearch::{query}:{CATEGORY}:{page}` 모양이 된다 (Spring 기본 동작으로 알고 있음, 실행 확인 안 함) | |
| Redis DB 번호 | 설정 없음 → `spring.data.redis.database` 기본 0 | yml/properties 어디에도 `database` 없음(grep) |
| 에러 핸들러 | `CacheErrorConfig implements CachingConfigurer` → `new LoggingCacheErrorHandler()` | `config/CacheErrorConfig.java:21-24` |

## 2. 캐시 어노테이션과 호출 경로 (코드로 확인)

- `@Cacheable`은 `TmdbSearchService.search(Category, String, int)` **하나뿐**. `@CachePut`/`@CacheEvict`는 프로젝트 전체에 없다(grep, 주석에만 언급). `RedisTemplate` 직접 사용도 없음.
- 호출 경로: `SearchController` → `SearchService.search` → `searchProviders`(private) → `providers.stream()...p.search(...)`. `p`는 주입받은 **다른 빈**(`TmdbSearchService`)이라 **프록시를 탄다**. 같은 클래스 내부 호출(self-invocation)이 아니다.
- `TmdbSearchService` 안에서 `search`를 스스로 다시 부르는 곳도 없다(`mapToResponse`/`isMatchingCategory`는 캐시와 무관한 private 헬퍼).
- `@Cacheable` 프록시는 `TmdbSearchService`가 `SearchProvider` 인터페이스를 구현하므로 JDK 인터페이스 프록시다(25-g 기록과 일치). `SearchService`가 `List<SearchProvider>`로 받으므로 문제 없음.
- `unless = "#result == null || #result.results == null || #result.results.isEmpty()"`: **빈 결과는 put하지 않는다**. 단, 이 조건은 put만 막는다. 메서드 호출 전의 **get(GET 명령)은 항상 나간다**.

## 3. TMDB vs Kakao: 실제로 캐시를 타는 것

| 경로 | 캐시 |
|---|---|
| MOVIE / TV / ANIME_* 단일 카테고리 | **TMDB만 캐시** (`tmdbSearch`, 키에 카테고리 포함) |
| ALL | `searchIsolated`가 모든 provider에 `search(ALL,...)` 호출 → TMDB는 캐시(키 `...:ALL:...`), Kakao는 캐시 아님 |
| ANIME | TMDB provider `findFirst` 후 필터링 → TMDB 캐시 |
| BOOK | `KakaoSearchService` → **캐시 없음** (`@Cacheable` 없음) |

즉 "영화/드라마 검색"을 했다면 `@Cacheable` 대상 경로가 맞고, 책 검색은 Redis를 전혀 쓰지 않는다.

## 4. spring.cache.* / spring.data.redis.* 결정 방식 (prod)

- 로컬 `application.yml`: `spring.data.redis.host/port(6379)/ssl.enabled(true)`, `spring.cache.type: redis`. 비밀번호는 `application-api-key.yml`(`spring.profiles.include: api-key`).
- **둘 다 이미지에 들어가지 않는다.** `demo/.dockerignore`가 `src/main/resources/application*.yml|yaml|properties`를 제외한다(확인). `Dockerfile`은 `SPRING_PROFILES_ACTIVE=prod`만 박고 Redis 관련 값은 없다. 따라서 **prod의 Redis 접속 정보는 전부 Cloud Run 환경변수에만 의존**한다.
- 이미지에 들어가는 Redis 관련 설정은 `ops-defaults.properties`의 `timeout=1s`, `connect-timeout=1s` 둘뿐이다. `ops-prod.properties`에는 Redis 관련 값 없음.
- 환경변수를 안 주면 Spring Boot 기본값: **host `localhost`, port 6379, ssl false, password 없음, database 0**.
- **prod에서 cache type이 redis가 아닌 값이 되어 캐시가 꺼질 가능성은 없다 (코드 근거).** `RedisCacheConfig`가 `CacheManager` 빈을 직접 만들기 때문에 Boot의 `CacheAutoConfiguration`(`@ConditionalOnMissingBean(CacheManager)`)이 물러나고 `spring.cache.type`은 사실상 읽히지 않는다. 25-b/25-f 기록과 같다. `SPRING_CACHE_TYPE=none` 같은 값을 줘도 이 빈이 우선이라고 판단하지만, 그런 환경변수를 주지 않았다는 가정과 별개로 **실행해서 확인한 것은 아니다**.
- 테스트 쪽 대비: `application-test.yml`은 `cache`를 `simple`로 둠(`TmdbSearchServiceCacheTest`는 `ConcurrentMapCacheManager`). 즉 **실제 Redis + `GenericJackson2JsonRedisSerializer` 경로를 통과하는 테스트는 저장소에 없다**(연결 거부/무응답 폴백 테스트만 있음).
- 25-f 문서(§4)는 `SPRING_DATA_REDIS_HOST`, `_PORT`, `_PASSWORD`, `_SSL_ENABLED=true`를 **수동으로 주입**해야 한다고 적어 두었다. 실제 Cloud Run 서비스에 이 값이 들어갔는지는 코드로 알 수 없다.

## 5. CacheErrorConfig의 로깅 (코드 + 알고 있는 Spring 동작)

- 확인된 것: `new LoggingCacheErrorHandler()`를 반환한다. 기본 생성자라 스택트레이스 없이 로그만 남기는 쪽이다(주석에도 "WARN 로그로 남기고 삼킨다"). `logging.*` 설정은 properties/yml에 없어 루트 INFO라 WARN은 출력된다.
- 아래 레벨/문구는 **Spring 6.x `LoggingCacheErrorHandler` 구현을 기억해서 적은 것이고 이번에 실행/소스로 확인하지 않았다**:
  - 레벨: **WARN** (Cloud Run 로그에서는 severity `WARNING`). **ERROR가 아니다.**
  - 로거: `org.springframework.cache.interceptor.LoggingCacheErrorHandler`
  - 문구: `Cache 'tmdbSearch' failed to get entry with key '<키>': <예외 toString>` / `... failed to put entry with key '<키>': ...` (evict/clear도 같은 꼴)
- **Cloud Run에서 검색할 키워드**: `failed to get entry with key`, `failed to put entry with key`, `LoggingCacheErrorHandler`, `tmdbSearch`. 예외 쪽 단서: `RedisConnectionFailureException`, `Unable to connect to Redis`, `Connection refused`, `localhost/127.0.0.1:6379`, `Command timed out`, `QueryTimeoutException`, `NOAUTH`, `WRONGPASS`.
- **심각도 필터를 ERROR 이상으로 걸었다면 이 로그는 보이지 않는다.** 실제로 "ERROR 없음, redis 연결 실패 로그 못 찾음"이라는 관찰과 정확히 맞는다. 로그 탐색은 severity 필터 없이(또는 WARNING 이상) 위 키워드로 해야 한다.
- 스택트레이스는 안 남는다(`logStackTrace=false`로 알고 있음). 한 줄 로그이므로 "Redis"라는 단어가 없을 수도 있다(예: `Cache 'tmdbSearch' failed to get entry ... : RedisConnectionFailureException: Unable to connect to Redis`처럼 예외명에만 들어감).
- 추가로 주의: 캐시 **키 계산(SpEL) 오류**나 **직렬화 오류**가 어디서 나느냐에 따라 핸들러를 거치지 않을 수도 있다. 현재 증상(오류 없음)과는 관계가 적어 확인하지 않았다.

## 6. 캐시 키/로그의 사용자 입력 노출 (코드로 확인)

- 키: `#query + ':' + #category + ':' + #page` → **검색어 원문이 Redis 키에 그대로 들어간다**(`tmdbSearch::<검색어>:MOVIE:1`). Upstash Data Browser에 검색어가 보이는 구조이고, 이는 의도된 설계지만 개인정보 관점에서는 노출 지점이다.
- 로그: ① `LoggingCacheErrorHandler`의 WARN에 **키 전체가 찍히므로 검색어가 로그에 남는다**(위 문구 기준, 미실행). ② `TmdbSearchService:84`의 `log.error("TMDB API 호출 중 예외 발생 [query: {}]", query, ...)`. ③ 25-g 발견 4번(RestTemplate 예외 메시지의 URL)과 같은 뿌리.
- 키 길이/특수문자 제한은 코드에 없다. 매우 긴 검색어가 그대로 키가 된다.

## 7. 원인 후보 (가능성 순)

관찰 요약: **키 없음 + Commands 0 + Bandwidth ~90B + ERROR 로그 없음**. Commands 0이 핵심 단서다. 앱이 Upstash에 **명령을 한 번도 못 보냈거나**, 보냈어도 **다른 인스턴스/DB**로 보냈다는 뜻이다. (정상 연결이면 `@Cacheable`이 호출될 때마다 최소 GET 1개가 나가므로, 빈 결과가 put을 막는 경우에도 Commands는 0이 아니어야 한다.)

| # | 후보 | 구분 | 근거 / 한계 |
|---|---|---|---|
| 1 | **Cloud Run에 `SPRING_DATA_REDIS_HOST`(/PORT)가 안 들어감 또는 오타** → 기본 `localhost:6379`로 연결 시도 → 연결 거부 → WARN 후 삼킴 → 외부 API 직접 호출 | **메커니즘은 코드로 확인**(yml이 이미지에 없음, 기본값 localhost, fail-open). **실제로 env가 빠졌는지는 추정** | Commands 0, ERROR 없음과 모두 일치. 25-f §4/§9가 "호스트를 빠뜨려도 앱은 정상 기동해 눈에 안 띈다"고 이미 경고했던 시나리오 |
| 2 | **`SPRING_DATA_REDIS_SSL_ENABLED=true` 누락** → Upstash(TLS 전용)에 평문으로 접속 → 핸드셰이크 실패/1초 timeout → WARN 후 삼킴 | 메커니즘 코드 확인(ssl 기본 false는 25-b에 기록, yml 미포함). 누락 여부는 추정 | 평문 접속은 Upstash가 명령으로 세지 않을 가능성이 크다(추정). 약 90B는 이런 거부 트래픽이나 대시보드 자체 호출일 수 있다(추정) |
| 3 | **env는 있으나 접속 대상이 Data Browser와 다름**: 다른 Upstash DB의 엔드포인트, 다른 계정/리전, 오래된 값 | 추정 | 이 경우에도 "내가 보는 인스턴스의 Commands 0"이 설명된다. 비밀번호가 맞아도 엔드포인트가 다르면 키는 그쪽에 쌓인다 |
| 4 | **비밀번호/사용자명 불일치**(시크릿 버전, ACL 사용자명 `default` 아님 → `SPRING_DATA_REDIS_USERNAME` 필요 가능성 25-f §4) → 인증 실패 → WARN 후 삼킴 | 추정 | 인증 실패 요청이 Upstash Commands에 잡히는지는 모름(AUTH만 오가고 GET은 못 감) |
| 5 | **1초 timeout 안에 연결 완료 못 함**: TLS 핸드셰이크+인증이 `timeout/connect-timeout=1s`를 넘김(리전 불일치 시 RTT가 큼, 콜드 연결) → 매번 timeout → WARN 후 삼킴 | 추정 | 25-g/25-f가 "RTT는 25-h에서 측정"으로 남긴 미확인 항목. 도쿄 대 도쿄면 가능성은 낮지만 실제 Upstash 리전은 확인하지 못함 |
| 6 | **확인 시점 문제**: TTL이 10분(`RedisCacheConfig:25`)이라 검색 후 10분 넘어 Data Browser를 봤으면 키가 이미 만료됨 | 코드로 확인(TTL), 사용자가 본 시점은 모름 | 단독으로는 Commands 0을 설명하지 못한다(만료돼도 사용량 그래프에는 남는다). 다만 사용량 그래프가 늦게 갱신되는 경우까지 포함하면 배제하지 말고 **검색 직후**에 다시 보는 것이 안전 |
| 7 | **TMDB 호출이 실패해 빈 결과 → put 안 함** | 코드로 확인(`unless`, `catch`에서 빈 응답) | 이것만으로는 설명이 안 된다. GET은 나가므로 Commands가 0이 아니어야 한다. 그래서 순위가 낮다. 다만 TMDB 토큰/네트워크 문제가 **함께** 있을 수는 있다(로그의 `TMDB API 호출 중 예외 발생` 확인 가치) |
| 8 | 검색을 BOOK(Kakao)으로 했거나 프론트 카테고리가 다른 경로 | 코드로 확인(BOOK은 캐시 없음) | 사용자는 영화/드라마라고 했으므로 낮음. 프론트 요청 카테고리는 이번에 읽지 않음 |
| 9 | 직렬화(`GenericJackson2JsonRedisSerializer`) 문제 | 추정 | `SearchResponse`/`UnifiedSearchResponse`는 `@NoArgsConstructor`+`@Getter`(세터 없음)라 역직렬화 가능한지 실제 Redis로 검증한 테스트가 없다. 하지만 이건 **GET/SET이 나간 뒤**의 문제라 Commands 0과 모순되어 후순위. 연결 문제가 풀린 뒤 별도로 확인할 항목 |

**배제된 후보(코드 근거)**: `@EnableCaching` 누락(있음), CacheManager 중복/충돌(`CacheManager` 빈은 `RedisCacheConfig` 하나), self-invocation(아님), `spring.cache.type`이 redis 아님(빈 직접 생성이라 무관), prod 프로파일에서 캐시가 꺼지는 설정(`ops-prod.properties`에 없음).

## 8. 발견 사항 (고치지 않음)

1. **Redis 연결 실패가 ERROR가 아니라 WARN 한 줄로만 남고 스택트레이스도 없다.** severity ERROR 알림/필터로는 영영 안 잡힌다. 지금 증상이 이것 때문에 "조용히" 보였을 가능성이 높다. (fail-open의 의도된 대가)
2. **기동 시/주기적으로 Redis에 실제로 붙는지 확인하는 신호가 없다.** `readiness`/`liveness`에서 Redis를 뺐고(25-e 결정) 기동 로그에도 Redis 연결 확인이 없어서, host 누락처럼 설정이 틀려도 앱이 정상으로 보인다. 25-f 문서가 이미 경고만 했을 뿐 자동 확인 수단은 없다.
3. 캐시 키와 WARN 로그에 **검색어 원문**이 들어간다(§6). 로그/키 정책 결정 필요(25-g 발견 4와 이어짐).
4. **실제 Redis 직렬화 경로 테스트 부재.** 테스트는 모두 메모리 캐시나 연결 실패 케이스만 다룬다. 연결이 되는 순간 처음 드러날 문제(역직렬화 등)가 있을 수 있다(후보 9).
5. `RedisCacheConfig` TTL/직렬화는 캐시 이름별 설정이 아니라 기본값 하나다. 캐시가 늘면 구분이 필요하다(범위 밖).
6. 25-f 문서의 TLS 누락 경고(`SPRING_DATA_REDIS_SSL_ENABLED=true`)는 문서에만 있고 **코드/설정으로 강제되지 않는다**. `ops-defaults.properties`에 기본값을 둘 수도 있지만(25-g 방식) 이번 task에서는 건드리지 않았다.

## 9. 확인된 사실 / 추정

**확인된 사실 (코드로)**
- `@Cacheable`은 `TmdbSearchService.search` 하나, 프록시를 정상 경유, self-invocation 없음.
- `CacheManager`는 `RedisCacheConfig`의 `RedisCacheManager` 하나, TTL 10분, 문자열 키 + Jackson JSON 값, 키 접두사/DB 번호 별도 설정 없음.
- Redis 접속 정보(yml)는 이미지에 없고 prod는 환경변수 의존. 빠지면 `localhost:6379`, ssl false.
- 오류 처리는 `LoggingCacheErrorHandler` → 예외 삼킴(fail-open). 빈 결과는 put 안 함, 책(Kakao) 검색은 캐시 안 탐.
- `spring.cache.type`은 prod에서 캐시 on/off를 결정하지 않는다.

**추정 (확인 안 됨)**
- `LoggingCacheErrorHandler`의 정확한 로거/문구/레벨(WARN), 스택트레이스 없음 (Spring 구현을 기억해서 적음).
- Cloud Run 환경변수에 `SPRING_DATA_REDIS_HOST`/`SSL_ENABLED`가 실제로 어떻게 들어가 있는지.
- Upstash가 평문/인증 실패 접속을 Commands로 세는지, 90B의 출처.
- 실제 Upstash 리전과 RTT, 1초 timeout 적정성.

## 10. 다음에 할 일 (이번에는 하지 않음; 순서 제안)

1. **Cloud Run 로그를 severity 필터 없이 검색**: `failed to get entry with key` / `LoggingCacheErrorHandler` / `Unable to connect to Redis` / `localhost/127.0.0.1:6379` / `Command timed out`. 나오는 예외 문구가 후보 1/2/4/5 중 어느 것인지 거의 바로 가린다. (가장 싸고 결정적)
2. 배포된 Cloud Run 리비전의 **환경변수/시크릿 이름 목록 확인**(값 노출 없이): `SPRING_DATA_REDIS_HOST`, `_PORT`, `_PASSWORD`, `_SSL_ENABLED=true`가 있는지, 엔드포인트가 Data Browser를 보는 그 DB와 같은지(후보 1, 2, 3).
3. 검색 **직후**(10분 TTL 이내)에 Data Browser `*` 재조회 및 사용량 그래프 갱신 확인(후보 6).
4. 원인이 설정 누락이면 별도 task에서: 필수 값 누락을 기동 시 드러내는 방법(기본값/검증/기동 로그) 검토, 실제 Redis 직렬화 통합 테스트(Testcontainers redis) 추가, 같은 검색어 2회 응답 시간 비교와 Upstash RTT 측정(25-f §11의 남은 항목).
5. 검색어가 키/로그에 남는 문제의 정책 결정(해시 키 또는 로그에서 키 마스킹).

## 11. 정리 (트러블슈팅/면접용)

1. **문제 상황**: prod에서 TMDB 검색 후 Upstash에 키가 없고 사용량은 0인데, Cloud Run 로그에는 ERROR가 없어 장애로 보이지 않았다.
2. **고려한 선택지**: ① 캐시 구성 자체 결함(어노테이션/프록시/CacheManager) ② 설정 결정 문제(환경변수/TLS/type) ③ fail-open이 오류를 삼켜서 관측이 안 되는 문제.
3. **선택 이유(코드로 좁힌 결과)**: ①은 코드상 배제(@EnableCaching·프록시·빈 하나 모두 정상). `spring.cache.type`도 직접 만든 CacheManager 빈 때문에 무관. 남은 것은 ② 접속 정보가 이미지에 없고 환경변수에만 의존한다는 점과 ③ 오류가 WARN 한 줄로만 남는다는 점. Commands 0이라는 사실이 "명령이 Upstash까지 도달하지 못함"을 가리킨다. 트레이드오프: fail-open은 Redis 장애가 검색 장애로 번지지 않게 해 주지만 그만큼 설정 오류도 안 드러낸다.
4. **막혔던 부분과 해결**: 외부(Cloud Run, Upstash)를 볼 수 없어 환경변수 실제 값과 로그 문구는 확인하지 못했다. 그래서 "코드로 확인"과 "추정"을 분리해 적고, 로그 검색 키워드와 확인 순서를 남겼다.

## 12. 복습 질문

<details>
<summary>Q1. Redis가 완전히 연결 안 되는데도 사용자는 검색이 잘 되고 Cloud Run에 ERROR도 없었다. fail-open이면 왜 장애가 눈에 안 보이는가? (CacheErrorConfig와 로그 레벨 기준으로)</summary>

답:

</details>

<details>
<summary>Q2. `@Cacheable`이 프록시 기반이라는 것은 무슨 뜻인가? 이 프로젝트에서 SearchService가 TmdbSearchService.search를 부를 때는 캐시가 적용되는데, 만약 TmdbSearchService 안의 다른 메서드가 같은 클래스의 search()를 직접 호출했다면 어떻게 되겠는가?</summary>

답:

</details>

<details>
<summary>Q3. (기존 코드 연결) 25-e에서 readiness 헬스체크에 Redis를 넣지 않았다. 이 결정과 이번 조사 결과(설정이 틀려도 앱이 정상으로 보임)는 어떤 트레이드오프로 연결되는가? 그리고 이를 보완하려면 어떤 신호가 필요한가?</summary>

답:

</details>
