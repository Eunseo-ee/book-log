# 25-a. 운영 설정을 환경변수로 주입하는 구조 확정

- 번호: 규칙은 `NN-이름.md`이지만 선례(`24-1-ci-test-report.md`, `21-1-email-unique.md`)가 `N-하위번호-이름` 형태라, 같은 방식으로 `25-a-runtime-config.md`를 골랐다. 커밋하지 않음.
- 목표: 비밀값/운영 설정을 코드·이미지가 아니라 환경변수로 주입. Dockerfile은 25-b.

## 코드 현황 확인 (요청서와 다른 점 포함)
- `ApiConfig`: `@Configuration + @ConfigurationProperties(prefix="api")`, 필드 kakaoKey/tmdbToken. 요청서와 같음.
- application.yml에는 DB URL/username, redis host/port/ssl, `cache.type`, `spring.profiles.include: api-key`, ddl-auto/show-sql/format_sql이 있다. password, kakao, tmdb, jwt, redis password는 `application-api-key.yml`에 있다. (값은 열람·출력하지 않고 키 이름만 확인)
- **요청서에 없던 설정**: `jwt.*` 3개(JwtProperties), `app.cors.allowed-origins`(SecurityConfig `@Value`, 기본값 `http://localhost:3000`), `spring.data.redis.*`. 표에 포함했다.
- H2: main 코드에 의존 없음(SecurityConfig의 H2 콘솔 주석 한 줄뿐). 테스트에서는 `application-test.yml`을 쓰는 `DemoApplicationTests`, `HighlightRepositoryTest`와, 기본 `Replace.ANY`로 임베디드 H2를 쓰는 `@DataJpaTest`들(HighlightFilterRepositoryTest, MemberEmailUniqueRepositoryTest, MemberIsolationRepositoryTest)이 쓴다.
- 확인 항목: `HighlightRepositoryTest`는 `replace: none`이지만 `@ActiveProfiles("test")`의 H2 URL(`application-test.yml`)을 그대로 쓰는 것이라 **실제 DB(Supabase)가 아니라 H2**다. "Replace.NONE이면 실DB"라는 추정은 이 프로젝트에서는 맞지 않는다. 수정하지 않았다.

## 변경한 파일
| 파일 | 변경 |
|---|---|
| `demo/build.gradle` | H2 `runtimeOnly` → `testRuntimeOnly` |
| `demo/src/main/java/book_log/demo/config/ApiConfig.java` | `@Validated` + 두 필드 `@NotBlank` |
| `demo/src/test/resources/application-test.yml` | 가짜 `api.kakao-key`, `api.tmdb-token` 추가 (없으면 contextLoads 실패) |
| `demo/src/test/java/book_log/demo/config/EnvironmentBindingTest.java` | 신규. 환경변수 이름 바인딩 검증 |
| `.env.example` (프로젝트 루트) | 신규. 변수 이름만 |
| `.gitignore` | `.env` 추가 (기존에는 `.env*.local`뿐이라 `.env`는 무시되지 않았음) |
| `.claude/tasks/25-a-runtime-config.md`, `.claude/decisions.md` | 신규 / 항목 추가 |

application.yml, application-api-key.yml, HighlightRepositoryTest, ci.yml은 건드리지 않았다. 운영 코드에 기본값을 넣지 않았다.

## 환경변수 표 (EnvironmentBindingTest로 검증한 이름)
| 환경변수 | 프로퍼티 | 필수 | 비밀 |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | spring.datasource.url | 필수 | 아니오(호스트 노출 주의) |
| `SPRING_DATASOURCE_USERNAME` | spring.datasource.username | 필수 | 예 |
| `SPRING_DATASOURCE_PASSWORD` | spring.datasource.password | 필수 | 예 |
| `SPRING_DATA_REDIS_HOST` | spring.data.redis.host | 필수(Redis 사용 시) | 아니오 |
| `SPRING_DATA_REDIS_PORT` | spring.data.redis.port | 필수 | 아니오 |
| `SPRING_DATA_REDIS_PASSWORD` | spring.data.redis.password | 필수(비번 있는 Redis) | 예 |
| `SPRING_DATA_REDIS_SSL_ENABLED` | spring.data.redis.ssl.enabled | 선택(기본 false) | 아니오 |
| `API_KAKAOKEY` (=`API_KAKAO_KEY`) | api.kakao-key | **필수, 없으면 기동 실패** | 예 |
| `API_TMDBTOKEN` (=`API_TMDB_TOKEN`) | api.tmdb-token | **필수, 없으면 기동 실패** | 예 |
| `JWT_SECRET` | jwt.secret (BASE64, 32바이트 이상) | 필수(JwtProperties가 검증) | 예 |
| `JWT_ACCESSTOKENEXPIRATION` | jwt.access-token-expiration (ms) | 필수 | 아니오 |
| `JWT_REFRESHTOKENEXPIRATION` | jwt.refresh-token-expiration (ms) | 필수 | 아니오 |
| `APP_CORS_ALLOWED_ORIGINS` | app.cors.allowed-origins | 운영에선 사실상 필수(기본값은 localhost) | 아니오 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | spring.jpa.hibernate.ddl-auto | `validate`로 지정 | 아니오 |
| `SPRING_JPA_SHOW_SQL` | spring.jpa.show-sql | `false`로 지정 | 아니오 |
| (주입 불가/불필요) | spring.jpa.properties.hibernate.format_sql | — | — |

## 바인딩 테스트 결과 (추측이 아니라 실행 결과)
- `API_KAKAOKEY` / `API_TMDBTOKEN` → 바인딩 **된다**.
- `API_KAKAO_KEY` / `API_TMDB_TOKEN` → **이것도 된다.** 처음에 "안 될 것"이라고 예상했으나 테스트가 반증했다. 프로퍼티 이름 `api.kakao-key`는 `-`를 `_`로 바꾼 `API_KAKAO_KEY`로도, `-`를 뺀 `API_KAKAOKEY`로도 읽힌다.
- 키가 하나라도 없음/공백 → 컨텍스트 기동 실패, 메시지에 `kakaoKey`/`tmdbToken` 필드 이름이 포함된다. 둘 다 없어도 실패.
- `JWT_ACCESSTOKENEXPIRATION`, `APP_CORS_ALLOWED_ORIGINS`(`@Value`는 `APP_CORS_ALLOWED_ORIGINS` 형태로 읽힘), `SPRING_JPA_*`, `SPRING_DATA_REDIS_SSL_ENABLED` 확인.
- **`hibernate.format_sql`은 환경변수로 만들 수 없다.** `..._FORMAT_SQL`은 키가 `hibernate.format.sql`, `..._FORMAT__SQL`도 같은 결과, `..._FORMATSQL`은 `hibernate.formatsql`이 되어 정확한 `format_sql`이 나오지 않는다(밑줄이 들어간 Map 키 문제).
  - 영향 없음: format_sql은 SQL 출력 모양만 바꾸며 show-sql=false면 쓰이지 않는다. 25-b에서 application*.yml이 이미지에 없으면 기본값 false다. 그래서 요구사항 5의 `format_sql=false`는 "설정하지 않음 = false"로 충족하고 따로 주입하지 않는다. 꼭 필요하면 `SPRING_APPLICATION_JSON`을 써야 한다(미검증).
- 검증 방식 주의: 환경변수 소스의 이름이 `systemEnvironment`가 아니면 Boot가 환경변수 이름 변환을 적용하지 않아(Map 바인딩에서 특히) 처음에는 모두 빈 결과가 나왔다. 소스 이름을 `systemEnvironment`로 맞추자 실제 동작과 같아졌다.

## 확정된 결정
1. 운영 설정은 새 yml 없이 환경변수로 주입(요청서 방향 유지). 로컬은 기존 yml 방식 유지.
2. ApiConfig 필드명은 유지, 환경변수를 코드에 맞춤. 표기는 ci.yml이 쓰는 `API_KAKAOKEY` 형태로 통일.
3. 키 누락 검증은 `@Validated + @NotBlank`. 테스트용 가짜 값은 `application-test.yml`에만 둔다.
4. H2는 `testRuntimeOnly`.
5. 운영 JPA: `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, `SPRING_JPA_SHOW_SQL=false`. format_sql은 주입하지 않음.

## 제안만 (구현하지 않음)
- yml을 이미지에 안 넣으면 `spring.application.name`, `spring.cache.type`, `spring.profiles.include`도 사라진다. **`spring.cache.type` 값을 확인하지 않은 채 25-b를 하면 캐시 동작이 바뀔 수 있어** 25-b 시작 시 확인 필요.
- 대안: `SPRING_PROFILES_ACTIVE=prod` + 비밀 없는 `application-prod.yml`(ddl-auto, show-sql, format_sql, cache.type 등)을 이미지에 포함. 설정 출처가 둘이 되는 대신 환경변수로 못 쓰는 키(format_sql)도 다룰 수 있다.

## ./gradlew test 결과 (demo/)
- BUILD SUCCESSFUL. XML 집계 **230개, 실패 0, 에러 0, skipped 9**. skipped 9는 `ContentRepositoryPostgresTest`(이 PC에서 Docker 데몬이 응답하지 않아 건너뜀, 24-1과 동일). 이번에 추가한 `EnvironmentBindingTest` 7개 통과.
- `DemoApplicationTests`(contextLoads)는 테스트 프로파일의 가짜 키로 통과.

## 발견한 문제 (고치지 않음)
1. **validate 위험**: ddl-auto: update로 쌓인 운영 DB와 엔티티가 다르면 validate가 기동을 막는다. 배포 전에 실제 Supabase 스키마와 대조가 필요하다(`90-check-real-schema.sql`). 이후 스키마 변경 수단(Flyway 등)이 없다.
2. DB URL/username이 application.yml에 하드코딩. gitignore 대상이지만 로컬 파일에 운영 계정 정보가 있다.
3. **CI 영향 가능성(미확인)**: ci.yml이 `API_KAKAOKEY: ${{ secrets.KAKAO_KEY }}`를 주입한다. 시크릿이 없으면 빈 문자열이 들어오고, 환경변수가 `application-test.yml`보다 우선이라 가짜 값을 덮어써 `@NotBlank`로 기동 실패할 수 있다. 로컬에서 재현하지 못했으니 push 후 CI 확인 필요. 대응(ci.yml에서 해당 env 제거 등)은 범위 밖이라 하지 않았다.
4. `JwtProperties`는 직접 검증, `ApiConfig`는 Bean Validation으로 방식이 다르다.
5. 환경변수 이름이 두 형태 모두 되므로 한 가지로 통일하지 않으면 헷갈린다.
6. `.env.example`을 루트에 뒀다. 25-b에서 docker compose `env_file` 위치 재확인.
7. 24-1에서 이어진 것: 테스트 폴더 `DemoApplication` 중복, 빈 `injectMocks.java`.

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 비밀값과 운영 설정이 gitignore된 yml에만 있어 컨테이너/배포에서 주입 경로가 없고, API 키가 비어도 앱이 떠서 첫 외부 API 호출에서야 실패했다. 운영에서 ddl-auto: update와 SQL 로그도 켜진 상태였다.
2. **고려한 선택지**: ① 환경변수로 통일 ② 운영용 yml 추가 ③ 시크릿 매니저/설정 서버.
3. **선택 이유**: ①. 이미지에 설정 파일을 넣지 않는 25-b 목표에 맞고 가장 단순하다. 트레이드오프: `format_sql`처럼 환경변수로 표현 못 하는 키가 있고, yml이 가졌던 기본값(application.name 등)을 잃는다.
4. **막혔던 부분과 해결**: 이름 규칙을 "API_KAKAO_KEY는 안 될 것"이라 추측했으나 테스트로 둘 다 된다고 확인. 테스트에서 환경변수 소스 이름을 `systemEnvironment`로 맞추지 않으면 Map 바인딩이 빈 결과라 원인 파악에 시간이 걸렸다. `@NotBlank` 추가로 깨질 contextLoads는 테스트 yml의 가짜 값으로 해결.

## 복습 질문
<details><summary>Q1. 환경변수 이름이 API_KAKAOKEY 형태가 되는 이유(relaxed binding)를 설명하라. 이 프로젝트에서 API_KAKAO_KEY도 되는 이유는?</summary>

(여기에 답)

</details>

<details><summary>Q2. 운영에서 ddl-auto를 update 대신 validate로 두는 이유와 위험은?</summary>

(여기에 답)

</details>

<details><summary>Q3. ApiConfig 키 누락 시 기동 실패를 시키는 것이 "나중에 런타임에서 발견"보다 나은 이유는? (기존 코드 연결: JwtProperties는 같은 목적을 어떻게 달성했나?)</summary>

(여기에 답)

</details>
