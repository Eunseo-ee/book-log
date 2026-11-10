# 25-j. Redis timeout 후속 조사: "무응답 시 요청당 약 2초" 가정 검증 (25-h 후속)

- 번호: 직전이 `25-i-cache-error-stacktrace.md`라 `25-j`로 이었다. 커밋하지 않음.
- 성격: **읽기 전용 조사**. 코드/설정/Dockerfile/CI/배포 설정 수정 없음, 외부 접속(Upstash, Cloud Run, gcloud) 없음, decisions.md 수정 없음(이번 조사로 확정된 결정이 없다).
- `./gradlew test`: **실행하지 않음**. 코드를 바꾸지 않았다. 아래 테스트 설명은 테스트 코드를 읽은 결과이고 이번에 통과시켜 본 것이 아니다.
- 비밀값: 읽지도 적지도 않았다. `application.yml`은 timeout 관련 키만 grep했다.
- 배경: Redis 캐시는 prod에서 정상화됨(원인은 비밀번호 시크릿 불일치, 코드 변경 없음). 25-h 체크리스트 "Redis가 느릴 때 요청당 최대 약 2초 지연 허용 여부"를 코드 기준으로 확인한다.
- 근거 소스: 이 저장소 코드 + 로컬 Gradle 캐시의 sources.jar(lettuce-core 6.4.1.RELEASE, spring-data-redis 3.4.1, spring-boot-autoconfigure 3.4.1, spring-context 6.2.1)를 열어 읽었다. 실행해서 측정한 값은 없다.

## 1. 결론 요약

1. **"@Cacheable은 실행 전 GET, 실행 후 PUT이라 Redis 무응답이면 두 번 기다린다"는 가정은 코드 기준으로 맞다.** 단, PUT은 `unless` 조건이 허용할 때만 나간다(빈 결과면 PUT 없음).
2. timeout은 **command 1초, connect 1초 둘 다 명시**돼 있다(`ops-defaults.properties`). "connect timeout 미설정" 상태는 아니다.
3. 명령 timeout만 보면 최악 추가 지연은 약 2초(GET 1 + PUT 1)다. 다만 **연결을 새로 맺어야 하는 상황(shared connection이 없을 때)에서는 연결 단계 지연이 명령 timeout에 더해질 수 있어 2초를 넘길 여지가 있다.** 이 부분은 소스를 일부만 읽은 추정이고, 측정은 없다(§2, §5).
4. 기존 테스트는 timeout **설정값**은 정확히 검증하지만, 지연 **시간**은 "8초 미만"이라는 느슨한 상한만 검증한다. "약 2초"를 검증하는 테스트는 없다(§3).

## 2. timeout 설정 위치와 현재 값 (코드 근거)

| 항목 | 현재 값 | 정의 위치 / 근거 |
|---|---|---|
| command timeout (`spring.data.redis.timeout`) | **1s** | `demo/src/main/resources/ops-defaults.properties:33`. `OperationsExposureConfig`가 `@PropertySource("classpath:ops-defaults.properties")`로 읽음(`OperationsExposureConfig.java:15`). Boot가 `LettuceConnectionConfiguration.applyProperties`에서 `builder.commandTimeout(...)`으로 전달(spring-boot-autoconfigure 3.4.1, 158-159행) |
| connect timeout (`spring.data.redis.connect-timeout`) | **1s** | `ops-defaults.properties:34`. Boot가 `SocketOptions.builder().connectTimeout(...)`으로 전달(`LettuceConnectionConfiguration.createClientOptions`, 175-177행) |
| 위 두 값을 덮어쓰는 방법 | 환경변수 `SPRING_DATA_REDIS_TIMEOUT`, `SPRING_DATA_REDIS_CONNECTTIMEOUT` | `ops-defaults.properties:31` 주석, decisions.md 2026-11-07. **prod Cloud Run에 이 환경변수가 실제로 들어갔는지는 코드로 알 수 없다 → 미확인** |
| `application.yml` 안의 timeout | **없음** | grep 결과 redis 블록에는 host/port/ssl.enabled, cache.type만 있음. (yml은 어차피 이미지에 안 들어간다: `.dockerignore`) |
| `RedisCacheConfig`의 timeout/ClientOptions/SocketOptions | **없음** | `RedisCacheConfig.java`는 TTL 10분(25행), null 캐싱 금지, 직렬화만 설정. `LettuceClientConfiguration`/`ClientOptions` 커스터마이저 빈도 프로젝트 전체에 없음(grep: `lettuce`, `ClientOptions` 소스에서 0건) |
| Dockerfile / ci.yml의 Redis timeout | 없음 | grep 결과 `TIMEOUT` 관련은 ci.yml의 `timeout-minutes: 20`뿐(Redis와 무관) |
| shutdown timeout (`spring.data.redis.lettuce.shutdown-timeout`) | **미설정 → 라이브러리 기본 100ms** | RedisProperties.Lettuce 기본값 `Duration.ofMillis(100)`(spring-boot-autoconfigure 3.4.1 `RedisProperties.java:468`), `LettuceClientConfiguration` 기본값도 100ms(spring-data-redis 3.4.1, 187행). **종료 시점에만 쓰이고 요청 지연과 무관** |
| SSL handshake timeout | **미설정 → Lettuce 기본 10초** | `io.lettuce.core.SslOptions` `sslHandshakeTimeout = Duration.ofSeconds(10)`(lettuce-core 6.4.1, 180행). Boot는 `ssl.bundle`이 있을 때만 SslOptions를 직접 만들며(`createClientOptions`, 178행 이후) 이 프로젝트는 bundle 미사용이라 기본 SslOptions가 쓰인다고 본다(추정). **connect-timeout 1s가 이 핸드셰이크 대기까지 끊어 주는지는 이번에 확인하지 못함 → 미확인** |
| Lettuce 명령 timeout 적용 방식 | Boot가 `TimeoutOptions.enabled()`를 켬 | `LettuceConnectionConfiguration.java:193`. 이게 있어야 Lettuce가 명령 단위 timeout을 실제로 건다 |
| 라이브러리 기본값(참고) | 명령 60초 / 연결 10초 | `RedisURI.DEFAULT_TIMEOUT = 60`(lettuce-core 223행), `SocketOptions.DEFAULT_CONNECT_TIMEOUT = 10`(36행). 25-g에서 timeout을 넣은 이유 |
| 기타 Lettuce 기본값 | autoReconnect=true, pingBeforeActivateConnection=true, requestQueueSize=Integer.MAX_VALUE, disconnectedBehavior=DEFAULT | `ClientOptions.java` 44, 50, 54, 60행. 연결이 끊긴 동안 명령이 큐에 쌓여 timeout까지 기다릴 수 있다는 해석의 근거(§4에서 추정으로 사용) |
| TCP keepalive / TCP user timeout | 미설정 → 기본 꺼짐 | `SocketOptions.DEFAULT_SO_KEEPALIVE=false`, `DEFAULT_TCP_USER_TIMEOUT_ENABLED=false`(42, 44행). 유휴 연결이 중간 장비에서 조용히 끊기는 경우를 감지하는 설정은 없다 |

## 3. 호출 구조와 지연 계산

### 3-1. 코드로 확인된 호출 순서
- `@Cacheable`은 `TmdbSearchService.search` 하나(`TmdbSearchService.java:41-43`). `unless = "#result == null || #result.results == null || #result.results.isEmpty()"`.
- `CacheAspectSupport`(spring-context 6.2.1): 먼저 `findInCaches`에서 GET(521행에서 예외를 `handleCacheGetError`로 넘김) → 실패하면 에러 핸들러가 삼키고 캐시 미스로 취급 → 메서드 실행 → `cachePutRequest.apply(cacheValue)`(580행)로 PUT. 같은 요청 스레드에서 **순차** 실행이다.
- `RedisCacheManager.builder(connectionFactory)`는 `nonLockingRedisCacheWriter`를 쓴다(`RedisCacheManager.java:310`). GET은 `connection.stringCommands().get(key)`(`DefaultRedisCacheWriter.java:149`), PUT은 `set(...)`(247행) 각각 1회다. **락 관련 추가 명령은 없다**(non-locking).
- 각 캐시 연산은 `connectionFactory.getConnection()`을 새로 호출한다(`DefaultRedisCacheWriter.java:401`).
- 에러 핸들러는 `LoggingCacheErrorHandler(true)`(`CacheErrorConfig.java`), 예외를 삼키고 WARN + 스택트레이스만 남긴다. 즉 **서비스 실패로 번지지 않고, 대가는 지연과 로그**다.

### 3-2. 상황별 최악 추가 지연 (구분해서)

| 상황 | 추가 지연 | 코드 근거 / 추정 구분 |
|---|---|---|
| **GET 실패** (연결은 있으나 무응답) | 최대 command timeout **1초** | 근거: `timeout=1s` + `TimeoutOptions.enabled()`. 테스트 `RedisTimeoutFallbackTest.redisHangs...`가 이 상황을 로컬 소켓으로 재현(단 시간은 8초 미만만 확인) |
| **PUT 실패** (같은 상황) | 최대 **1초** | 근거: 같은 설정. **빈 결과면 `unless`로 PUT 자체가 없다**(`:43`) → 이 경우 지연은 GET 1초뿐 |
| GET + PUT | 약 **2초** | 가정 확인됨(§1-1). 이게 decisions.md 2026-11-07 "약 2초"의 근거 |
| **연결 거부** (포트 닫힘, RST 즉시) | 거의 0 (수 ms) | 추정(일반적인 TCP 동작). `CacheFailureFallbackTest`/`RedisTimeoutFallbackTest.a`가 이 상황을 쓰지만 시간은 재지 않는다 |
| **연결 자체가 안 됨, 패킷이 버려짐**(블랙홀: 방화벽/egress 차단 등) | 연산당 connect timeout **1초** 상당 | 근거: `connect-timeout=1s`. 추정: shared connection이 만들어지지 않으면 GET, PUT 각각이 연결을 다시 시도하므로 합 약 2초. `getSharedConnection()`의 재시도 동작은 1246행 시그니처만 보고 본문은 읽지 않았다 → **추정** |
| **연결을 새로 맺는 중 TLS/인증 단계에서 멈춤** (Upstash는 TLS+AUTH) | connect 1초 + 이후 단계가 더해질 수 있음. **상한 미확인** | 근거: SSL 핸드셰이크 기본 10초(§2), `pingBeforeActivateConnection=true`라 연결 후 PING 왕복이 추가됨. 이 단계들이 connect-timeout 1s/command timeout 1s 중 어느 것으로 끊기는지는 **미확인**. `RedisTlsTimeoutTest`가 TLS 무응답에서 8초 안에 끝나는 것까지만 본다 |
| **한 번 연결된 뒤 끊김 → autoReconnect 중** | 명령이 큐에 대기하다 command timeout 1초에 실패할 가능성 | 근거: autoReconnect=true, `disconnectedBehavior=DEFAULT`. 큐잉 후 timeout으로 끝난다는 건 **추정** |

정리: **정상 연결 상태에서 Redis가 멈추는 경우는 코드 근거로 약 2초가 맞다.** 연결을 새로 맺어야 하는 경우는 "약 2초"가 아니라 "2초 ~ 그 이상(상한 미확인)"으로 보는 편이 안전하다. 테스트의 8초 상한도 이 불확실성을 반영한 값으로 보인다(추정).

### 3-3. 참고: 요청 전체 지연에서의 비중
- 외부 API 호출 자체의 timeout은 `ApiConfig.java:32-33`에서 connect 3초, read 3초다. Redis 2초는 외부 API 정상 호출 앞뒤에 **더해지는** 값이다. 외부 호출이 느릴 때와 Redis 장애가 겹치면 합산해서 본다(추정, 합산 구조는 코드로 직접 계산하지 않음).
- 책(Kakao) 검색은 `@Cacheable`이 없어 Redis 지연의 영향이 없다(25-h §3).

## 4. 기존 테스트가 검증하는 것

| 테스트 | 검증 내용 | timeout 값 / 지연 시간 검증 여부 |
|---|---|---|
| `service/CacheFailureFallbackTest` | 운영 `RedisCacheConfig`+`CacheErrorConfig`를 가져오되 Redis 연결을 **아무도 listen하지 않는 포트**로 돌림. 검색 2회가 예외 없이 결과를 반환하고 외부 API가 2번 호출됨(캐시 불가) | **검증 안 함.** `LettuceConnectionFactory("localhost", port)`를 직접 만들어 `ops-defaults.properties`도 안 읽는다. 즉 이 테스트는 1초 설정을 쓰지 않고 Lettuce 기본값 상태다(연결 거부라 즉시 실패하니 문제는 안 드러남). 시간 측정 없음 |
| `service/RedisTimeoutFallbackTest` | `RedisAutoConfiguration`+`OperationsExposureConfig`+운영 캐시 설정으로 **운영과 같은 경로**로 구성. 4개 케이스 | |
| ↳ `defaultTimeoutsAreShort` | `LettuceConnectionFactory`에서 command timeout=1초, `SocketOptions.connectTimeout`=1초를 읽어 단언 | **설정값은 검증함.** 지연 시간은 아님 |
| ↳ `timeoutsAreOverridable` | `spring.data.redis.timeout=250ms`, `connect-timeout=300ms`로 덮어쓰면 반영됨 | **설정값만** (환경변수 이름 자체는 `EnvironmentBindingTest` 쪽) |
| ↳ `redisConnectionRefused_proceedsWithoutCache` | 포트 닫힘, 2회 호출이 각각 15초 `assertTimeoutPreemptively` 안에 끝나고 외부 API 2회 호출 | 15초 상한뿐. **연결 실패 지연을 수치로 검증하지 않음** |
| ↳ `redisHangs_finishesWithinTimeoutAndProceeds` | 연결만 accept하고 침묵하는 로컬 ServerSocket. 결과 반환, 외부 API 1회, 경과 시간 **8초 미만** | 8초 미만만 단언. **"약 2초" 가정은 검증하지 않음** (60초 기본값이면 깨진다는 의미의 회귀 방지) |
| `config/RedisTlsTimeoutTest` | `ssl.enabled=true`인데 TLS 핸드셰이크에 응답 안 하는 로컬 서버. `StringRedisTemplate.get`이 예외로 끝나고 8초 미만(15초 `assertTimeoutPreemptively`) | 8초 미만만. **SSL 핸드셰이크 timeout(기본 10초)과 1초 connect timeout 중 무엇이 끊었는지는 구분하지 못함.** 이 테스트가 통과한다는 건 "10초 핸드셰이크 기본값으로 매달리지는 않는다"는 정도의 증거 |
| `config/EnvironmentBindingTest` | 환경변수 이름이 프로퍼티로 바인딩되는지(Redis ssl 등). 실제 연결 없음 | timeout 지연 검증 아님 |
| `service/TmdbSearchServiceCacheTest` | `ConcurrentMapCacheManager`(메모리)로 캐시 히트/미스 동작 | Redis/timeout과 무관 |

이번에 위 테스트를 **실행하지 않았다.** 위 설명은 코드를 읽은 결과다.

## 5. 확인하지 못한 것
1. prod Cloud Run에 `SPRING_DATA_REDIS_TIMEOUT` / `SPRING_DATA_REDIS_CONNECTTIMEOUT`가 별도로 들어가 있는지(코드로 알 수 없음, 외부 접속 금지).
2. 실제 Upstash RTT와 정상 상태에서의 GET/SET 소요 시간. 1초가 정상 지연에 충분히 여유 있는지는 측정값이 없다(25-f/25-g가 남긴 항목이 계속 열려 있음).
3. 연결을 새로 맺는 경로의 정확한 상한: `LettuceConnectionFactory.getSharedConnection`/`getOrCreateSharedConnection` 본문, 연결 후 PING/AUTH 단계가 어느 timeout에 묶이는지, SSL 핸드셰이크 10초가 connect-timeout 1초와 어떻게 겹치는지.
4. 한 번 끊긴 shared connection이 요청마다 재연결을 시도하는지, 백그라운드 재연결만 하는지.
5. 연결 거부 시 실제 소요 시간(수치). 테스트가 재지 않음.
6. 동시 요청이 많을 때 Redis 지연이 스레드 점유로 쌓이는 정도(Tomcat 스레드 수, Cloud Run 동시성 설정 미확인).

## 6. 선택지 (구현하지 않음, 설명만)

| | (a) 현행 유지 (command 1s / connect 1s) | (b) command timeout 낮추기 (예: 300~500ms) | (c) connect timeout 명시 |
|---|---|---|---|
| 현재 상태 | 이미 둘 다 1s가 명시돼 있음 | 값만 환경변수로 바꿔도 됨(`SPRING_DATA_REDIS_TIMEOUT`, 코드 변경 없이 가능) | **이미 `connect-timeout=1s`가 명시돼 있어 "추가"가 아니라 "값 조정/검증 강화"의 의미.** 25-h 질문이 "connect 미설정"을 전제했다면 그 전제는 틀리다 |
| 장점 | 변경 없음. 도쿄-도쿄 정상 상태에선 충분히 여유가 있을 가능성(미측정). 정상 RTT가 느린 순간의 불필요한 캐시 포기를 줄임 | Redis 장애 시 사용자 지연이 2초에서 1초 안팎으로 줄 수 있음. 캐시는 부가 기능이라는 설계 원칙과 맞음 | 연결 단계 지연을 따로 제한하므로 블랙홀/방화벽 상황에서의 상한이 명확해짐. 값을 command보다 작게 두면 연결 실패를 더 빨리 포기 |
| 단점 | 장애 시 요청당 약 2초(연결 재시도가 끼면 더) 지연. 장애가 길면 모든 TMDB 검색이 느려짐. 서킷 브레이커 없이 매 요청이 다시 기다림 | 콜드 연결(TLS 핸드셰이크+AUTH)이나 일시적 지연에서 정상인데도 timeout → 캐시 미스가 늘어 외부 API 호출 증가. 스택트레이스 WARN도 증가(25-i 임시 설정과 겹침) | 너무 짧으면 TLS 연결 직후 첫 요청이 계속 실패할 수 있음. connect timeout이 TLS 핸드셰이크까지 포함하는지가 미확인이라 효과를 단정할 수 없음 |
| 위험 | 실제 RTT를 모른 채 "충분하다"고 가정하는 것(측정 필요) | Upstash RTT/콜드스타트 측정 없이 낮추면 캐시가 사실상 꺼진 것처럼 동작할 수 있고, 그 사실이 WARN으로만 남음 | 위와 같음 + 값 변경이 테스트(`defaultTimeoutsAreShort`가 1s를 단언)와 충돌하므로 테스트 갱신 필요 |

**추천(한 줄)**: 코드는 바꾸지 말고 (a) 현행 유지하되, Upstash RTT를 먼저 측정한 뒤 값이 나오면 환경변수로만 (b)를 판단한다(근거: 설정이 이미 명시돼 있고 측정 없이 낮추면 캐시 효과를 잃을 위험이 지연 절약보다 큼).

## 7. 발견한 문제 (고치지 않음)
1. **25-h에 남아 있는 문구가 현재 사실과 어긋난다.** 25-h 본문은 원인을 "미확인 후보 목록"으로 두고 있는데, 지금은 WRONGPASS(시크릿 불일치)로 확정·해결됐다. 해결 기록은 별도 갱신이 필요하다(이번에는 기존 파일을 건드리지 않음).
2. **`CacheFailureFallbackTest`는 운영 timeout 설정을 쓰지 않는다**(`LettuceConnectionFactory("localhost", port)` 직접 생성). 이름과 달리 "운영 설정 하에서의 폴백"이 아니라 "Lettuce 기본값 하에서의 연결 거부 폴백"이다. 같은 케이스는 `RedisTimeoutFallbackTest.a`가 운영 경로로 다시 다룬다.
3. **지연 상한 테스트가 느슨하다.** 8초 미만만 단언하므로 timeout 설정이 1초에서 3초로 바뀌어도 통과한다. 값 자체는 `defaultTimeoutsAreShort`가 지키므로 "값 변경은 막지만 시간 회귀는 못 잡는" 구조다.
4. **스택트레이스 로깅(25-i 임시 진단)이 아직 켜져 있다.** Redis 지연/장애가 길어지면 요청마다 스택트레이스 2개(GET/PUT)가 WARN으로 쌓인다. 원인이 해결됐으니 되돌릴지 결정이 필요하다(decisions.md 2026-11-10 항목: 원인 확인 후 재결정으로 남겨 둠).
5. **서킷 브레이커/실패 기억이 없다.** Redis가 계속 죽어 있어도 모든 요청이 매번 GET/PUT에서 timeout을 새로 기다린다(코드에서 실패 상태를 기억하는 구조 없음, grep 기준).
6. **SSL 핸드셰이크 timeout(기본 10초)이 별도로 조정되지 않았다.** connect-timeout 1s로 끊기는지 미확인이라, TLS 단계에서 멈추는 장애에는 상한이 불명확하다(`RedisTlsTimeoutTest`도 8초 미만만 본다).
7. `spring.data.redis.timeout` 환경변수 덮어쓰기는 `ops-defaults.properties` 주석과 decisions.md에만 있고, 배포 런북(`25-f`)에 반영돼 있는지는 이번에 확인하지 않았다.

## 8. 사용자가 다음에 할 일
1. (선택) 정상 상태의 Upstash RTT 측정: 같은 검색어를 2회 호출해 캐시 미스/히트 응답 시간 비교, Cloud Run 로그에서 `Cache 'tmdbSearch' failed` WARN이 더 나오지 않는지 확인.
2. 25-i 임시 스택트레이스 로깅을 유지/되돌릴지 결정.
3. §5의 미확인 3번(연결 단계 상한)이 필요하면 별도 task로: `LettuceConnectionFactory` 본문 확인 + 블랙홀 연결(로컬에서 패킷을 버리는 방식)로 실제 소요 시간 측정 테스트 검토.
4. 값 조정이 필요하다고 판단되면 먼저 환경변수(`SPRING_DATA_REDIS_TIMEOUT`)로만 시험하고, decisions.md에 반영할지 결정.
5. 25-h 문서를 "해결됨(WRONGPASS)"으로 정리할지 결정.

## 9. 복습 질문

<details>
<summary>Q1. command timeout과 connect timeout의 차이는 무엇이고, 이 프로젝트에서 어느 쪽이 더 큰 위험인가?</summary>

답:

</details>

<details>
<summary>Q2. 캐시 장애가 서비스 장애로 번지지 않게 하려면 어떤 설계가 필요한가?</summary>

답:

</details>

<details>
<summary>Q3. @Cacheable이 메서드 전후에 Redis를 두 번 호출(GET, PUT)하는 구조는 timeout 설정에 어떤 영향을 주는가?</summary>

답:

</details>
