# 25-c. 타임존 일관화 (Clock 빈 주입)

- 번호: 25-a, 25-b와 같은 규칙. 커밋하지 않음.
- 목표: "오늘"을 서버(JVM) 시간대가 아니라 코드가 Asia/Seoul로 정하게 하고, 시간을 테스트에서 고정할 수 있게 한다.

## 작업 전 현황: 현재 시각을 직접 읽는 곳 (src/main 전체 검색)
| 위치 | 코드 | 이번 범위 |
|---|---|---|
| `ContentService.java` saveContent | `viewDate.isAfter(LocalDate.now())` (미래 날짜 검증) | 변경 |
| `StatisticsController.java` getMonthlyStats | `LocalDate.now().getYear()/getMonthValue()` (기본 연/월) | 변경 |
| `JwtTokenProvider.java:52` | `new Date()` (토큰 발급/만료 계산) | 기록만 |
| `BaseTimeEntity.java` | `@CreatedDate`/`@LastModifiedDate` LocalDateTime (JPA Auditing, JVM 기본 시간대) | 기록만 |
- `LocalDateTime.now()`, `Instant.now()`, `ZonedDateTime.now()`, `System.currentTimeMillis()`는 src/main에 없다. (src/test에는 있음, 아래 참고)
- JwtTokenProvider의 `new Date()`는 epoch 밀리초라 시간대와 무관하다(만료 판정에 영향 없음). BaseTimeEntity의 createdAt은 JVM 시간대의 벽시계 값이 시간대 정보 없이 저장되므로 TZ가 바뀌면 값이 달라질 수 있다 → 발견 사항.

## 발견한 문제 (고치지 않음)
1. **요청서가 말한 month null NPE는 이미 없다.** 컨트롤러는 task 19(decisions 2026-11-01)에서 기본값을 먼저 채운 뒤 범위 검증하도록 고쳐져 있다(코드 확인: `targetMonth` 계산 → 그 뒤 `targetMonth < 1 || > 12`). 그래서 기본 연/월 경로에 도달할 수 있어 테스트가 막히지 않았다.
2. `BaseTimeEntity`의 createdAt/modifiedAt은 JVM 기본 시간대 기준 LocalDateTime(Auditing). Clock을 쓰지 않으므로 TZ 환경변수가 빠지면 UTC로 저장될 수 있다. 하이라이트 응답의 createdAt도 이 값이다. 이번 범위 밖(요청서 지정).
3. `JwtTokenProvider`의 `new Date()`: 시간대와 무관하지만 Clock이 아니라 토큰 만료 테스트에서 시간을 고정할 수 없다(현재 테스트는 실제 시각 + 60초 방식).
4. `ContentServiceTest`의 기존 테스트들은 여전히 JVM 기본 시간대의 `LocalDate.now()`로 날짜를 만든다. 서비스에 같은 시간대의 시계(systemDefaultZone)를 줘서 일관되게 통과하지만, 운영 시계(KST)와는 다른 시계로 검증하는 셈이다. 경계는 신규 테스트 2개가 KST 고정 시계로 따로 검증한다.

## 변경/추가 파일
| 파일 | 내용 |
|---|---|
| `config/TimeConfig.java` | 신규. `Clock.system(ZoneId.of("Asia/Seoul"))` 빈 |
| `service/ContentService.java` | `Clock` 필드(@RequiredArgsConstructor 생성자 주입), `LocalDate.now(clock)` |
| `controller/StatisticsController.java` | `Clock` 필드, `today = LocalDate.now(clock)` 한 번만 읽어 연/월 모두 사용 |
| 테스트 5개 수정/추가 | 아래 |
| `.claude/tasks/25-c-timezone-clock.md`, `decisions.md` | 신규 / 항목 추가 |

변경하지 않은 것: application*.yml, BaseTimeEntity, JwtTokenProvider, DB 쿼리, HighlightRepositoryTest, 25-d 이후 항목.

`today`를 한 번만 읽은 이유: 연과 월을 각각 `now()`로 읽으면 자정 경계에서 두 호출 사이에 월이 바뀌어 (연, 월)이 어긋날 수 있다(예: 12/31 23:59:59.999에 연=올해, 1/1에 월=1). 부수 효과로 코드가 짧아졌다.

## 기존 테스트 수정 (assertion 의도 유지)
| 테스트 | 이유 | 수정 |
|---|---|---|
| `ContentServiceTest` | 서비스 생성자에 Clock 추가 → `@InjectMocks`가 Clock을 못 채워 null | `@Spy Clock = Clock.systemDefaultZone()` 필드 추가. 기존 테스트들이 JVM 기본 시간대의 `LocalDate.now()`와 비교하므로 같은 시간대 시계를 줘서 기존 동작을 그대로 유지. assertion 변경 없음 |
| `CorsConfigTest`, `MemberIdPropagationTest`, `ProtectedApiAccessTest` | `@WebMvcTest`는 `@Configuration`(TimeConfig)을 스캔하지 않아 StatisticsController 생성 시 `NoSuchBeanDefinitionException: Clock` (처음 실행에서 이 3개 + 아래가 실패) | `@Import`에 `TimeConfig.class` 추가만. assertion 변경 없음 |
| `StatisticsControllerTest` | 같은 이유 + 기존 3개 테스트가 `LocalDate.now()`와 비교 | 가변 Clock `@TestConfiguration` 주입. 기존 테스트의 기대값을 `LocalDate.now()`에서 고정 시계(`Clock.fixed(instantNow, KST)`)의 날짜로 바꿈. "기본값이 현재 연/월이다"라는 의도는 같고 비교 기준만 서비스가 쓰는 시계로 맞춤. 400 검증 테스트는 변경 없음 |

## 추가한 테스트 (4개)
| 테스트 | 시각 | 확인 |
|---|---|---|
| `ContentServiceTest.saveContent_BoundaryBeforeKstMidnight` | 2026-05-31T14:59Z (KST 5/31 23:59) | 5/31 저장 허용, 6/1 거부 |
| `ContentServiceTest.saveContent_BoundaryAfterKstMidnight` | 2026-05-31T15:01Z (KST 6/1 00:01) | 6/1 저장 허용, 6/2 거부 |
| `StatisticsControllerTest.defaultsAreMayJustBeforeKstMidnight` | 14:59Z | 기본값 2026-5 |
| `StatisticsControllerTest.defaultsAreJuneJustAfterKstMidnight` | 15:01Z | 기본값 2026-6 (UTC 날짜는 5월임을 테스트 안에서 단언) |

두 번째 시각은 UTC 기준이면 날짜가 5/31이라, 구 코드(UTC 서버)였다면 6/1 기록이 "미래"로 거부되고 기본 월이 5월이었을 경계다.
**미확인**: 이 테스트들이 구 코드(`LocalDate.now()`)에서 실패함을 직접 돌려 보지는 않았다. (이 PC가 KST면 구 코드도 통과하므로, 시계 고정으로 시간대와 무관하게 검증된다는 점이 핵심.)

## ./gradlew test (cleanTest 후 재실행)
- BUILD SUCCESSFUL, **234개, 실패 0, 에러 0, skipped 9** (25-b의 230개 + 신규 4개).
- skipped 9는 25-b와 같은 `ContentRepositoryPostgresTest`(Testcontainers가 Docker 29를 인식 못 함). 통과가 확인되지 않은 상태 그대로다.
- 처음 구현 직후 실행에서는 `@WebMvcTest` 4개 클래스(Cors/MemberIdPropagation/ProtectedApiAccess/StatisticsController)가 Clock 빈 없음으로 실패했고, 위 수정 후 통과했다.
- 실제 앱 기동(Spring 전체 컨텍스트에서 TimeConfig 로딩)은 `DemoApplicationTests`(H2)가 통과하는 것으로만 확인했고, Docker 이미지로 다시 띄워 보지는 않았다.

## 결정과 이유
1. Clock 빈(Asia/Seoul) 주입. 이중 방어: Dockerfile의 TZ(환경) + Clock(코드).
2. 고려한 대안:
   - ① `LocalDate.now(ZoneId.of("Asia/Seoul"))` 직접 호출: 가장 단순하고 생성자 변경도 없다. 하지만 시간을 고정할 수 없어 경계 시각 테스트가 불가능하다.
   - ② Clock 빈 주입(채택): 시간 고정 가능, TZ 환경변수에 의존하지 않음. 비용: 생성자 변경으로 기존 `@InjectMocks`/`@WebMvcTest` 테스트 수정 필요(실제로 4개 클래스 + ContentServiceTest).
   - ③ TZ 환경변수/`-Duser.timezone`만: 설정 한 줄이지만 빠지면 조용히 UTC로 돌고 테스트로 못 잡는다.
3. DB 쪽 날짜 비교는 `view_date`(DATE) 컬럼과 서비스가 계산한 [시작일, 다음 기간 시작일) 범위 파라미터라 JVM 시간대와 무관하다(task 17). 이번에 건드리지 않음.
4. Clock 빈 위치는 config 패키지의 별도 `TimeConfig`(ApiConfig는 API 키 전용 @ConfigurationProperties라 섞지 않음).

## 막힌 부분과 해결
- `@WebMvcTest`가 `@Configuration`을 스캔하지 않아 Clock 빈이 없다는 실패 → `@Import(TimeConfig)`(고정이 필요한 곳은 테스트용 Clock 빈)로 해결.
- 요청서의 "month null NPE로 기본값 경로 테스트가 막힐 수 있다"는 이미 해결된 상태라 막히지 않았다(발견 1번).

## 검증한 것 / 못 한 것
- 한 것: 전체 테스트 234개 통과(위), KST 경계 4개 테스트.
- 못 한 것: 구 코드에서의 실패 재현, Postgres 쿼리 테스트 9개(skip), 컨테이너(UTC 강제)에서의 실제 동작 확인(`TZ` 제거 후 `docker run`으로 확인 가능, 이번엔 안 함).

## 다음 task(25-d) 제안
CORS 운영 origin 설정(`APP_CORS_ALLOWED_ORIGINS`)과 `.env.example` 주의 주석(`SPRING_DATA_REDIS_SSL_ENABLED`, `?prepareThreshold=0`). 이후 25-e: 쓰기 엔드포인트 인증/노출 점검, HEALTHCHECK, actuator, Swagger 운영 비공개 여부. 별도 후보: BaseTimeEntity/JwtTokenProvider의 시각을 Clock으로 통일할지 결정.

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 컨테이너 기본 시간대는 UTC라 `LocalDate.now()`가 KST 새벽(0~9시)에 하루 전 날짜를 반환한다. 오늘 날짜 기록이 "미래"로 거부되거나 월초 통계 기본값이 지난달이 되고, 시간을 고정할 수 없어 경계 테스트도 못 쓴다.
2. **고려한 선택지**: ① `now(ZoneId)` 직접 호출 ② Clock 빈 주입 ③ TZ 환경변수만.
3. **선택 이유**: ②. 시간대를 코드가 소유하고(환경변수가 빠져도 KST) 테스트에서 `Clock.fixed`로 경계를 재현한다. 트레이드오프: 생성자 변경으로 기존 테스트 수정 비용.
4. **막혔던 부분과 해결**: `@WebMvcTest`가 `@Configuration`을 안 읽어 Clock 빈 없음으로 4개 클래스가 실패 → `@Import`/테스트용 Clock 빈. `now()` 두 번 호출은 자정 경계에서 (연, 월)이 어긋날 수 있어 한 번만 읽도록 함.

## 복습 질문
<details><summary>Q1. 컨테이너에 TZ=Asia/Seoul을 넣었는데도 왜 코드에서 Clock으로 한 번 더 막았나?</summary>

(여기에 답)

</details>

<details><summary>Q2. Clock을 주입하면 테스트에서 무엇이 가능해지나? 이번 테스트에서 14:59Z와 15:01Z를 고른 이유는?</summary>

(여기에 답)

</details>

<details><summary>Q3. (기존 코드 연결) 통계 쿼리가 view_date(DATE)와 서비스가 계산한 [시작일, 다음 기간 시작일) 범위로 집계되는데, 왜 JVM 시간대의 영향을 받지 않나? 그러면 시간대의 영향을 받는 값은 무엇인가?</summary>

(여기에 답)

</details>
