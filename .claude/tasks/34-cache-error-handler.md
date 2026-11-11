# 34. 캐시 오류 핸들러 개선 (task 33 추천안 c′ 구현)

- 번호: 직전이 `33-cache-error-logging.md`라 `34`. 커밋하지 않음.
- 범위: 캐시 핸들러만. `TmdbSearchService`/`KakaoSearchService` 로그, 로그 레벨, Redis 설정은 건드리지 않음.

## 1. 변경 내용
| 파일 | 변경 |
|---|---|
| `config/MaskingCacheErrorHandler.java` (신규) | `CacheErrorHandler` 직접 구현. get/put/evict/clear 모두 WARN 한 줄만 남기고 예외를 삼킴 |
| `config/CacheErrorConfig.java` | `new LoggingCacheErrorHandler(true)` → `new MaskingCacheErrorHandler()`, 25-i 임시 주석과 import 제거 |
| `test/.../config/MaskingCacheErrorHandlerTest.java` (신규) | 12개 단위 테스트 |
| `.claude/decisions.md` | 결정 1건 추가(맨 아래 줄) |

로그 형식(실제 테스트 로그에서 확인):
`WARN ... MaskingCacheErrorHandler : Cache 'tmdbSearch' failed to get entry [key#d4d38ad1]: RedisConnectionFailureException, root cause java.net.ConnectException: Connection refused: getsockopt`
- clear는 키가 없어 `[key#...]` 부분이 없다.
- 키 해시: `String.valueOf(key)`의 UTF-8 SHA-256 앞 8자(검색어/카테고리/페이지를 쪼개지 않음). null 키는 "null"의 해시.
- 방어: 메시지 개행·제어문자를 공백으로, 200자 제한, 원인 사슬 깊이 20 제한(순환 대비), 로깅 중 RuntimeException은 삼킴, WARN이 꺼져 있으면 아무것도 안 함.

## 2. 테스트 결과 (직접 실행)
- `cd demo && ./gradlew test` → **BUILD SUCCESSFUL**. 결과 XML 합계(직접 집계): tests 363, skipped 9, failures 0, errors 0 (직전 351 + 신규 12).
- skipped 9는 `ContentRepositoryPostgresTest`(이 실행에서 Postgres 테스트는 실행되지 않음. Docker가 꺼져 있으면 skip되는 구조라는 기존 기록 기준이며, 이번에 Docker 상태는 조회하지 않음).
- 신규 `MaskingCacheErrorHandlerTest` 12개 통과: 4개 동작 모두 예외 비전파 / 키 원문 없음·해시 앞 8자·캐시 이름 포함 / 예외 클래스·루트 메시지 포함, `Caused by:`·개행·스택 줄 없음, `warn(Object, Throwable)` 미호출 / 같은 키 같은 해시·다른 키 다른 해시 / 키 null·빈 문자열·공백 / 메시지·캐시 null / 순환 원인 / 긴 메시지 / WARN 비활성 / 운영 설정이 이 핸들러를 사용.
- 기존 캐시 관련 테스트 수정 없이 통과: `CacheFailureFallbackTest`(1), `RedisTimeoutFallbackTest`(4), `RedisTlsTimeoutTest`(1) failures 0, skipped 0. `RedisTimeoutFallbackTest` 로그에서 새 핸들러의 실제 출력(위 형식)을 확인.
- **미확인(수동 확인 필요)**: 실제 Upstash/운영 Cloud Logging에서의 출력. 루트 메시지에 접속 대상(호스트)이나 비밀번호가 들어가는지는 실제 Redis 예외로 못 봤다(테스트의 ConnectException 메시지에는 호스트가 없었음). 외부에 공유할 때는 마스킹할 것.

## 3. 내가 로컬에서 확인하는 절차 (Redis 접속 정보를 일부러 틀리게)
1. 로컬에서 Redis 설정만 틀리게 둔다. 예: `SPRING_DATA_REDIS_PORT=6390`(안 열린 포트) 또는 비밀번호를 틀린 값으로(실제 비밀번호를 터미널에 남기지 않도록 `SPRING_DATA_REDIS_PASSWORD=wrong`). 파일(application.yml)은 수정/출력하지 말고 환경변수로만 덮어쓴다.
2. 앱을 띄우고 TMDB 영화 검색을 한 번 실행한다(예: 검색어 "테스트검색어").
3. 콘솔에서 `failed to get entry`를 찾는다. 기대: WARN **한 줄**, `[key#xxxxxxxx]`, 예외 클래스, `root cause ...`. 확인할 것: ① 검색어 "테스트검색어"가 로그에 **없음** ② `Caused by:`/`at ...` 줄 없음 ③ 검색 응답은 정상(결과는 TMDB에서 옴, 단 TMDB 토큰이 있어야 함) ④ 같은 검색을 다시 하면 같은 `key#` 값.
4. 포트 오류와 비밀번호 오류의 루트 원인 줄이 서로 다르게 보이는지 본다(인증 실패는 루트 메시지에 WRONGPASS 계열이 나오는 것으로 알려져 있으나 이 프로젝트 로그로는 미확인).
5. 확인 뒤 환경변수를 원복한다. 운영 Redis를 일부러 끊는 확인은 하지 않는다.

## 4. 발견한 문제 / 후속 (고치지 않음)
1. **후속 task(검색어 로깅)**: `TmdbSearchService.java:84`가 검색어를 ERROR 로그에 직접 남기고(`[query: {}]`), 그 `e.getMessage()`/`KakaoSearchService.java:88`/`SearchService.java:137`의 예외 객체에 URL(쿼리스트링)이 들어가 검색어가 새는 경로가 있다(후자는 추정). Cloud Run 요청 로그의 쿼리스트링은 미확인. 이번 변경으로는 이 경로가 줄지 않는다.
2. 해시 앞 8자(32비트)는 서로 다른 키의 충돌이 가능하다. 장애 진단용 구분에는 충분하다고 보지만 식별자로 쓰면 안 된다.
3. 스택트레이스는 이제 어디에도 없다. 프레임 단위 원인 추적이 필요하면 임시로 핸들러를 바꿔 재배포해야 한다(루트 원인 한 줄로 부족한 경우의 비용).
4. 핸들러 로거 이름이 `book_log.demo.config.MaskingCacheErrorHandler`로 바뀌었다. 기존에 `org.springframework.cache.interceptor.LoggingCacheErrorHandler`로 Cloud Logging 필터/알림을 만들었다면 갱신 필요(알림 설정 유무는 미확인).
5. 에디터 진단: 인터페이스 메서드 파라미터에 `@NonNull`/`@Nullable` 주석이 없다는 경고(기능 무관, 수정하지 않음).
6. 키를 `:` 연결로 만들어 생기는 키 충돌 가능성(33 §10-8)은 이번에도 다루지 않음.

## 5. 정리 (노션용)
1. **문제 상황**: 임시 진단용 `LoggingCacheErrorHandler(true)`가 켜진 채라, 캐시 장애 때 요청당 스택트레이스 2개가 쌓이고 캐시 키로 사용자 검색어 원문이 WARN에 남았다.
2. **고려한 선택지**: (a) true 유지, (b) false(메시지만; 원인 단서 소실, 키는 그대로), (c′) 커스텀 핸들러(한 줄 요약+키 해시).
3. **선택 이유**: (c′)는 스택을 끄면서도 루트 원인 한 줄로 인증/연결/timeout을 구분할 수 있고, 키를 SHA-256 앞 8자로 바꿔 검색어가 로그에 남지 않는다. 같은 검색어는 같은 해시라 반복 실패를 묶어 볼 수 있다. 트레이드오프: 클래스 하나·테스트 추가, 해시로는 검색어를 알 수 없고 프레임 단위 추적 불가.
4. **막혔던 부분과 해결**: `LoggingCacheErrorHandler`는 키를 메시지 안에서 포맷해 버려서 상속으로 키만 가릴 수 없었다. 그래서 `CacheErrorHandler`를 직접 구현했다. 로깅 도중 예외가 나면 fallback이 깨지므로 null 키/메시지/순환 원인에도 던지지 않게 만들고 테스트로 고정했다. Log를 Mockito로 주입해 "예외 인자를 넘기는 warn이 호출되지 않음"으로 스택트레이스 부재를 검증했다.

## 6. 복습 질문

<details>
<summary>Q1. 스택트레이스를 끄고 키를 해시로 바꾼 이유는? 각각 무엇을 얻고 무엇을 잃나요?</summary>

답:

</details>

<details>
<summary>Q2. 캐시 오류를 예외로 전파하지 않고 삼켜도 되는 이유는 무엇이고, 삼킨 뒤 요청은 어떻게 처리되나요?</summary>

답:

</details>

<details>
<summary>Q3. (기존 코드 연결) TmdbSearchService.search의 @Cacheable은 Redis가 죽었을 때 GET과 PUT 단계에서 각각 어떻게 동작하고, unless 조건은 PUT에 어떤 영향을 주나요?</summary>

답:

</details>
