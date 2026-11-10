# 25-f. Cloud Run 첫 배포 준비 (코드/설정 변경 + 배포 런북)

- 번호: 25-a~e, 25-g와 같은 `25-x-이름.md` 규칙. 커밋하지 않음.
- 목표: "첫 배포를 할 수 있는 상태"를 만든다. **실제 gcloud 실행, 배포, DB/Redis 접속은 하지 않았다.** 이 파일의 런북은 사용자가 직접 실행한다.
- 이 파일의 값은 전부 placeholder(`<...>`)다. 실제 비밀 값은 어디에도 쓰지 않았다.
- **2차 수정(결정 반영)**: 사용자가 §12의 결정 대부분을 확정해 §3-3, §4, §5, §6, §10, §11, §12, §14를 갱신했다. 코드 변경 없음(문서와 `.env.example` 주석만).
- **3차 수정(미확정 항목 확정)**: 리전(asia-northeast1), maxScale 2 / Hikari 풀 5, 서비스 계정·리소스 이름, JWT_SECRET 버전 고정, Vercel 확정을 §4, §6, §8, §10, §12, §14, §15에 반영했다. 코드 변경은 바인딩 확인 테스트 1개(`HikariEnvBindingTest`)뿐이다.

## 1. 현황 (코드로 확인한 것)
| 항목 | 확인 결과 |
|---|---|
| Dockerfile 위치 | `demo/Dockerfile` (루트 아님). 25-b 이후 `SPRING_PROFILES_ACTIVE` 없었음 → 25-d/25-e가 "25-f에서 지정"으로 넘긴 항목 |
| prod의 Swagger 비활성 | `ops-prod.properties`(`@Profile("prod")`)가 springdoc 둘 다 false. `ProdExposureTest`가 빈 부재/401을 이미 검증 |
| prod의 CORS 필수 | `SecurityConfig`가 prod + origin 빈 값이면 `IllegalStateException`. `CorsOriginsTest`가 **메서드 단위**로만 검증하고 있었음 (실제 기동 실패는 미검증) |
| liveness/readiness 노출 | `ops-defaults.properties`에 probes 활성 + 그룹 정의. `ProdExposureTest`가 두 경로 익명 200, Redis 장애 시에도 200, root health는 503을 검증 |
| Hikari 풀 설정 | 코드·`build.gradle`·`ops-*.properties` 어디에도 **없음** → Boot 기본값(최대 10). 로컬 yml에는 풀러용 `prepareThreshold=0`이 URL에 있음(값은 열람·기록 안 함) |
| `spring.cache.type` | 로컬 `application.yml`에 `redis`(키만 확인). `application-test.yml`은 `simple`. 이미지에는 yml이 없어 **미설정**. 아래 §9 |
| Docker 데몬 | **이 PC에서 응답 없음** (`docker version` → npipe 연결 실패). 이미지 빌드/실행 검증 불가 |

## 2. 변경 내용
| 파일 | 내용 |
|---|---|
| `demo/Dockerfile` | 런타임 스테이지에 `ENV SPRING_PROFILES_ACTIVE=prod` 한 줄 + 이유 주석 |
| `demo/src/test/java/book_log/demo/config/ProdProfileDefaultTest.java` | 신규 2개: ① Dockerfile 런타임 스테이지에 ENV가 있는지 ② prod + origin 빈 값이면 **앱이 실제로 기동 실패**하는지 (`SpringApplicationBuilder`, H2 test 프로파일) |
| `demo/src/test/java/book_log/demo/config/RedisTlsTimeoutTest.java` | 신규 1개: `ssl.enabled=true`인데 서버가 TCP만 받고 TLS 핸드셰이크에 무응답일 때의 timeout |
| `demo/src/test/java/book_log/demo/config/HikariEnvBindingTest.java` | (3차) 신규 1개: 환경변수 `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5`가 실제 `HikariDataSource`의 최대 풀 크기 5가 되는지 (임베디드 H2, 외부 DB 접속 없음) |
| `.env.example` | (2차) `SPRING_PROFILES_ACTIVE` 주석에 "이미지 기본값이 prod라 로컬 `docker run`/compose에도 `APP_CORS_ALLOWED_ORIGINS` 필요" 추가 |
| `.claude/tasks/25-f-cloud-run-first-deploy.md`, `.claude/decisions.md` | 신규 / 항목 추가 |

건드리지 않은 것: application*.yml, 25-a~e/g의 코드와 기존 테스트, ops-*.properties, ci.yml.

## 3. 결정과 이유
1. **`ENV SPRING_PROFILES_ACTIVE=prod`를 Dockerfile에 넣는다.** 25-b/25-d/25-e와 충돌 없음: ① 25-b는 "설정은 환경변수"라는 방침이고 ENV 기본값 한 줄은 그 안에서 덮어쓸 수 있다(Cloud Run 환경변수가 우선). 비밀이 아니라서 "이미지에 비밀을 넣지 않는다"에도 걸리지 않는다 ② 25-d의 "prod 빠뜨리면 localhost만 허용" 보호와 ③ 25-e의 "prod 빠뜨리면 Swagger가 열린다"(발견 1번, 열린 쪽으로 깨지는 문제)를 이미지 기본값으로 막는다.
   - 트레이드오프: 이 이미지를 **로컬에서 그냥 `docker run`하면 prod로 떠서** `APP_CORS_ALLOWED_ORIGINS`가 없으면 기동 실패하고 Swagger가 없다. 25-b의 로컬 스모크 절차를 다시 쓸 때는 CORS 변수를 주거나 `SPRING_PROFILES_ACTIVE=`를 덮어써야 한다.
   - 한계: Cloud Run에서 누가 `SPRING_PROFILES_ACTIVE`를 다른 값으로 **덮어쓰면** 다시 열린다. 이건 테스트로 막을 수 없고 런북 체크리스트(§10-0)와 25-h 스모크(`/swagger-ui.html` → 401)로 잡는다.
2. **테스트 보강 판단**: Swagger 비활성(`ProdExposureTest`)은 이미 충분히 있어 추가하지 않았다. CORS 필수는 메서드 단위만 있어서 "실제 앱 기동이 실패한다"를 추가했다. Dockerfile에 ENV가 있는지는 텍스트 검사라 약하지만(이미지를 안 빌드) 줄이 지워지는 회귀는 잡는다. 대안(이미지 빌드 후 확인)은 Docker가 필요해 CI 기본 경로로 부적합(23번 결정과 같은 이유).
3. **Springdoc 이중 방어(`SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false`) — Dockerfile에는 넣지 않고, 배포 환경변수(런북)에 넣는 쪽을 권장.** 판단:
   - Dockerfile ENV prod가 있으면 prod를 잃는 경로는 "배포 설정에서 누가 다른 프로파일로 덮어쓰는 것"뿐이다. 이때 springdoc 변수가 있으면 문서 내용은 안 나간다. 다만 프로파일이 prod가 아니면 SecurityConfig의 Swagger permitAll이 살아나 요청이 인증 없이 catch-all 500이 된다(25-e 결정 3번) — 문서는 안 새지만 "열린 경로 + 500"이 된다. 그리고 prod가 아니면 CORS는 fail-fast도 꺼진다(localhost 기본값).
   - 즉 의미는 "문서 내용 노출 차단"에 한정된 **얕은 보험**이다. 값이 이미지에 박히면 로컬에서 같은 이미지를 쓸 때 Swagger를 쓸 수 없고 출처가 또 하나 늘어난다. 비용이 거의 없는 배포 환경변수 2줄이라 런북 env 파일에는 넣되, 코드/Dockerfile에는 넣지 않는다. **→ 확정됨(§12): 배포 환경변수(service YAML)에만 둔다. 이유: prod 프로파일이 이미 Swagger를 끄고 `ProdExposureTest`가 보장하므로 설정 출처를 늘리지 않는다.**
4. **헬스체크는 §5 그대로** (25-e 결정을 반복하지 않고 gcloud/YAML로 옮기기만 함).
5. **Redis timeout 1초 유지**(§8) — 확정됨. 별도 조정 근거가 아직 없고, Upstash 리전 지연 때문에 오탐(정상인데 timeout)이 있는지는 25-h에서 실제 값을 보고 판단한다.

## 4. 환경변수 / 시크릿 분류 (25-a 표 기준)
`SPRING_PROFILES_ACTIVE`는 Dockerfile 기본값이 있어도 Cloud Run에서 한 번 더 명시하는 것을 권장(실수로 지워져도 보이도록).

| 환경변수 | 분류 | 비고 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | 일반 | Dockerfile 기본값과 동일하게 명시 |
| `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` | 일반 | **배포 전 §6 스키마 대조 필수** |
| `SPRING_JPA_SHOW_SQL=false` | 일반 | |
| `APP_CORS_ALLOWED_ORIGINS` | 일반 | 스킴 포함, 경로/쿼리 없음, 쉼표 구분, `*` 금지 (25-d-1) |
| `JWT_ACCESSTOKENEXPIRATION`, `JWT_REFRESHTOKENEXPIRATION` | 일반 | ms 단위 |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` | 일반 | Upstash 엔드포인트 호스트는 비밀이 아니다 |
| `SPRING_DATA_REDIS_SSL_ENABLED=true` | 일반 | **빠뜨리면 TLS Redis에 평문으로 붙는다** (25-b 표) |
| `SPRING_DATA_REDIS_TIMEOUT`, `..._CONNECTTIMEOUT` | 일반(선택) | 기본 1s. 바꿀 때만 |
| `SPRINGDOC_API_DOCS_ENABLED=false`, `SPRINGDOC_SWAGGER_UI_ENABLED=false` | 일반(확정) | service YAML에만. Dockerfile에는 넣지 않음 (§3-3) |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5` | 일반 | 이름 **검증됨**(`HikariEnvBindingTest`). §6-5 |
| `SPRING_DATASOURCE_URL` | 일반 (시크릿으로 올려도 무방) | 비밀번호는 없지만 DB 호스트/프로젝트 식별자가 들어 있다. 25-a도 "호스트 노출 주의"로 표기 |
| `SPRING_DATASOURCE_USERNAME` | **시크릿** | |
| `SPRING_DATASOURCE_PASSWORD` | **시크릿** | |
| `SPRING_DATA_REDIS_PASSWORD` | **시크릿** | |
| `API_KAKAOKEY` | **시크릿** | 없으면 기동 실패(`@NotBlank`) |
| `API_TMDBTOKEN` | **시크릿** | 없으면 기동 실패 |
| `JWT_SECRET` | **시크릿** | BASE64, 디코딩 후 32바이트 이상. **교체하면 발급된 모든 토큰이 무효**(서버가 키로만 검증) |

### 시크릿 주입 방식 — **확정: A. Secret Manager 참조**
- 이유: Cloud Run 환경변수는 콘솔(서비스 설정)에 평문으로 보인다. 트레이드오프: 단계가 늘고(API 활성화, 시크릿 생성, 서비스 계정에 `secretAccessor` 권한) 권한/이름 오타로 첫 배포가 막힐 수 있다. 대상 6개: DB username, DB password, Redis password, Kakao 키, TMDB 토큰, `JWT_SECRET`. 권한 부여 단계는 §10-2.
- 아래 B열은 비교 기록으로만 남긴다(채택 안 함).

| | A. Secret Manager 참조 (채택) | B. 환경변수 직접 주입 (채택 안 함) |
|---|---|---|
| 방법 | 시크릿 생성 → 서비스 계정에 접근 권한 → 서비스에 `SECRET:버전`을 환경변수로 연결 | `--set-env-vars`/YAML에 값을 직접 |
| 장점 | 값이 서비스 설정·YAML·셸 기록에 남지 않는다. 접근 로그/버전/교체가 분리됨. 서비스 설정을 열람 가능한 사람에게도 값이 안 보인다 | 설정이 단순하고 별도 API 활성화/권한이 없다 |
| 단점 | API 활성화 + 서비스 계정 권한(`roles/secretmanager.secretAccessor`) 한 단계 더. `latest`로 참조하면 값 변경이 **새 리비전 때** 반영되고, 버전 고정이면 교체마다 서비스 갱신 필요. 시크릿 이름/권한 오타로 첫 배포가 막힐 수 있음 | 값이 Cloud Run 서비스 설정(콘솔/`describe`)과 YAML 파일, 셸 히스토리에 남는다. 실수로 YAML을 커밋할 위험 |
| 이 프로젝트 | 시크릿 6개(DB user/pw, Redis pw, Kakao, TMDB, JWT) | 규모는 작아서 가능하지만 위 노출 면적이 더 큼 |

**버전 정책(확정)**: 기본은 `latest` 참조(새 리비전이 뜰 때 최신 버전을 읽음), **`JWT_SECRET`만 버전을 고정**한다(첫 버전 번호, 실제 번호는 사용자가 확인). 이유: `JWT_SECRET`이 바뀌면 모든 사용자 토큰이 무효가 된다(발견 7). 트레이드오프: `JWT_SECRET`을 교체할 때는 YAML의 버전 번호를 고쳐 다시 배포해야 한다. 고정한 버전을 비활성화/삭제하면 리비전이 뜨지 않을 수 있다(미확인). Secret Manager 참조 문법(`key`에 버전)은 계속 **미검증**.

## 5. 헬스체크 (Cloud Run probe)
- 코드 확인: `/actuator/health/liveness`, `/actuator/health/readiness`는 prod에서 익명 200 + `{"status":"UP"}`(`ProdExposureTest`). root `/actuator/health`는 Redis 포함이라 **쓰지 않는다**(Redis 장애 시 503 — 테스트로 확인된 사실).
- startup = readiness (DB 포함, Redis 제외), liveness = liveness (프로세스 상태만). **liveness에는 DB가 포함되지 않는다**: DB 장애가 컨테이너 재시작으로 번지지 않게 하기 위함(25-e).
- 주의: startup이 readiness(`db` 포함)라서 **Supabase에 연결 못 하면 리비전이 Ready가 안 되어 배포가 실패**한다. **의도된 동작으로 유지하기로 확정**했다(DB에 못 붙는 인스턴스로 트래픽을 보내지 않기 위해). 대신 startup probe의 period/failureThreshold는 Spring Boot 기동 + Hikari 최초 연결 시간을 감안해 넉넉히 잡는다. 무료 플랜 Supabase가 일시중지 상태면 이 때문에 실패하므로 배포 전 DB가 깨어 있는지 확인.
- 아래 숫자(YAML의 period/failureThreshold)는 **근거 없는 넉넉한 출발값**이다. JVM 기동 시간을 측정하지 않았다(25-e가 "25-f에서 재라"고 넘겼으나 이 PC에서 이미지를 못 띄워 측정 불가). **첫 배포 로그의 기동 시간(`Started DemoApplication in N seconds`)을 보고 조정**한다. Cloud Run이 허용하는 startup probe 최대 시간 등 제약은 공식 문서로 확인 필요(미검증).
- gcloud 플래그 형태는 버전마다 달라서 확신이 없다. **service YAML(`gcloud run services replace`)을 기본**으로 쓴다(§10-3).

## 6. DB 연결 (Supabase)과 풀러
**확인 절차 (사용자가 Supabase 대시보드에서 직접)**
1. 대시보드의 Connect/Database 설정에서 연결 문자열(direct / session pooler / transaction pooler)의 **호스트·포트·사용자명 형식**을 확인한다. 이번에 쓸 것은 session pooler(5432). 프로젝트 리전은 도쿄(확정)이며 풀러 호스트도 같은 리전인지 함께 본다. **로컬이 지금 쓰는 풀러 포트도 같이 확인**한다(로컬 yml에 `prepareThreshold=0`이 있어 transaction pooler일 가능성이 있다. 값은 열람하지 않았다). 풀러는 사용자명에 프로젝트 식별자가 붙는 형식인 것으로 알고 있다(확인 필요).
2. **확정: Supabase session pooler(5432)로 시작한다.** 이유: Cloud Run에서 direct 연결은 IPv6 문제가 있을 수 있어 풀러가 필요하다(**Supabase 공식 문서로 확인 필요** — 내가 아는 내용이며 검증하지 않았다). session pooler는 prepared statement 문제가 없어 단순하다.
3. URL 형식(placeholder): `jdbc:postgresql://<session-pooler-host>:5432/<db>`. 비밀번호는 URL에 넣지 않고 `SPRING_DATASOURCE_PASSWORD`로 분리한다. 로컬 yml에 있는 `prepareThreshold=0`은 transaction pooler용이라 session pooler에서는 넣지 않아도 되는 것으로 알고 있다(확인 필요).
4. **연결 수 관리(트레이드오프)**: session pooler는 클라이언트 연결 하나가 서버 연결 하나를 점유하므로 인스턴스가 늘면 연결 수가 그대로 늘어난다. 그래서 `maxInstances`(YAML `maxScale`)를 낮게 두고 Hikari 풀 크기를 작게 잡는다. 허용 연결 수는 Supabase 대시보드(요금제/풀러 설정)에서 확인한다.
5. **Hikari (확정: 풀 크기 5, maxScale 2)**: 코드·`build.gradle`·`ops-*.properties`·로컬 yml 어디에도 Hikari 설정이 없다(키 이름만 grep) → Boot 기본값(최대 10). 환경변수 `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5`로 줄인다. **이름은 `HikariEnvBindingTest`로 검증했다**: systemEnvironment 소스로 넣으면 실제 `HikariDataSource.getMaximumPoolSize()`가 5이고, 이름을 일부러 틀린 값으로 바꾸면 테스트가 실패하는 것도 확인했다(테스트가 헛돌지 않음). 언더스코어 형태(`..._MAXIMUM_POOL_SIZE`)가 된다는 것이 확인된 것이고, `..._MAXIMUMPOOLSIZE` 형태는 시도하지 않았다.
   - 계산: `maxScale 2 × 풀 5 = 최대 연결 10 안팎`. session pooler는 인스턴스 × 풀 크기만큼 연결을 점유한다. **Supabase 풀러 연결 한도는 사용자가 대시보드에서 확인**하고, 한도를 넘으면 값을 낮춘다(미확정 목록).
   - 한계: 풀 5는 근거 있는 측정값이 아니라 연결 수 상한을 10으로 맞춘 출발값이다. 동시 요청이 몰리면 풀 대기(Hikari 기본 connection timeout)가 생길 수 있어 25-h에서 확인한다. 배포 중 두 리비전이 잠시 동시에 떠 있을 때의 연결 수는 확인하지 않았다(발견 13).
6. **후속 옵션(지금은 하지 않음)**: 연결 부족이 실제로 생기면 transaction pooler(6543)로 전환하고 그때 JDBC URL에 `prepareThreshold=0`을 함께 넣는다. 전환 시 세션 상태를 쓰는 코드 여부는 발견 3번.

**코드에서 읽은 풀러 충돌 후보 (기록만)**: Hikari 설정 없음(기본값), `@Transactional` 사용은 일반적 수준, 세션 고정 상태(`SET`, advisory lock, `LISTEN`)를 쓰는 코드는 **이번에 전수 조사하지 않았다**(transaction pooler에서 문제가 되는 유형). 발견 사항 3번에 남김.

## 7. 스키마 대조 (ddl-auto=validate)
`validate`는 엔티티의 **테이블/컬럼/타입**이 DB에 있는지만 확인하고 **유니크 제약·인덱스는 확인하지 않는다.** 그래서 두 단계가 필요하다.

> **범위 결정**: 90번이 `content`만 보는 문제는 이 task에서 고치지 않는다. **별도 task 25-f-1로 분리**하고 **25-f-1 완료 전에는 첫 배포 금지**(§10-0). 25-f-1은 **다음 세션에 바로 진행**한다(확정).

**전체 테이블 대조 (25-f-1에서 추가): `.claude/tasks/22-perf/91-check-all-tables.sql`** — 읽기 전용 SELECT. `content`/`highlight`/`members` 전부의 컬럼·타입(A), id 자동 생성(B), 유니크·인덱스·FK·NOT NULL(C), 제약을 새로 만들 때 막힐 데이터(D). **실행 순서·정상 기준은 `25-f-1-schema-check.md` §3**: Supabase SQL Editor에서 쿼리를 A-1 → A-2 → B-1 → B-2 → C-1~C-4 → D-1~D-7 순서로 **하나씩** 선택 실행하고, "정상 결과"(대부분 0행)와 다르면 **배포 중단** 후 결과를 그대로 가져온다. 이 SQL은 PostgreSQL에서 아직 실행해 보지 않았다(미검증).

**기존 도구**: `.claude/tasks/22-perf/90-check-real-schema.sql` — 읽기 전용(SELECT 4개), `content` 테이블만 대상. 91번이 같은 항목(90 (1)(2)=91 C-1, 90 (3)=91 D-4)을 포함하므로 91번을 돌리면 충분하지만 90번도 그대로 쓸 수 있다.
- (1) 인덱스 목록: `uk_content_member_external_category`, `idx_content_member_view_date`가 보여야 한다
- (2) 제약 목록: 유니크/FK/PK
- (3) `(member_id, external_id, category)` 중복 행 — **0행이어야 정상**
- (4) 행 수 참고

**절차 (사용자가 Supabase SQL Editor에서 직접. Editor는 마지막 결과만 보여 주므로 쿼리를 하나씩 선택 실행)**
1. 90번의 (1)~(4)를 하나씩 실행해 결과를 기록한다. (1)에 두 인덱스가 없거나 (3)이 0행이 아니면 **배포 중단**, 원인을 먼저 해결(제약은 코드가 아니라 SQL로 정리 — 21-1 결정).
2. 90번은 `content`만 본다. `members` 등 나머지 테이블과 컬럼 타입은 이 파일이 확인해 주지 않는다. 가장 안전한 방법은 **운영 DB를 건드리지 않고** 스키마만 덤프해 로컬 PostgreSQL에 복원한 뒤, 같은 코드로 `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`로 기동해 보는 것이다(`pg_dump --schema-only` 등, 명령과 로컬 환경은 사용자 몫).
3. 간이 대안: 엔티티별 컬럼 목록을 `information_schema.columns`(SELECT)로 보고 엔티티와 눈으로 대조. 정확도가 낮으므로 2번 권장.
4. 불일치가 나오면 `validate`는 **기동 실패**(리비전 Ready 안 됨)로 드러난다. 이후 스키마 변경 수단(Flyway 등)이 없다는 점은 25-a 발견 1번 그대로이고 이 task 범위 밖이다.

## 8. Redis (Upstash 같은 TLS Redis)
**연결 절차**
1. Upstash에서 **도쿄 리전**으로 데이터베이스를 만든다(확정: DB·Redis·서버 간 지연 최소화). 호스트, 포트, 비밀번호를 확인한다. TLS 엔드포인트인지 확인(보통 TLS 필수).
2. 환경변수: `SPRING_DATA_REDIS_HOST`(일반), `SPRING_DATA_REDIS_PORT`(일반), `SPRING_DATA_REDIS_PASSWORD`(시크릿), **`SPRING_DATA_REDIS_SSL_ENABLED=true`**(일반, 필수). yml이 이미지에 없으므로 이 값은 기본 false다(25-b 표의 "중요" 항목).
3. ACL 사용자명이 `default`가 아닌 구성이면 `SPRING_DATA_REDIS_USERNAME`이 필요할 수 있다(25-a 표에 없는 변수, 확인 필요).
4. Redis 리전은 Cloud Run 리전(asia-northeast1, 도쿄)과 같게 한다(확정). RTT는 이 task에서 **측정하지 않았다**(25-h).
5. 확인: 배포 후 로그에 `Redis` 연결 오류 WARN이 없는지, 같은 검색어를 두 번 불렀을 때 두 번째 응답이 빨라지는지(25-h).

**timeout 기본값 1s를 운영에서 그대로 쓸지 → 유지**
- 근거: 같은 리전의 Redis 응답은 보통 수 ms~수십 ms라 1초는 정상 지연을 넘기고 무한 대기를 막는다. 25-g 결정을 뒤집을 새 정보가 없다. 최악의 경우 요청 하나가 get/put에서 각각 최대 1초(약 2초) 느려지고, Redis가 완전히 죽어 연결 거부면 즉시 실패해 거의 지연이 없다.
- **확정: 1s 유지.** 별도 조정 근거가 아직 없다. Upstash 리전 지연으로 오탐이 있는지는 25-h에서 실제 값을 보고 판단한다.
- 조정 기준: 25-h에서 실제 Upstash RTT를 재서 p99가 수백 ms 근처면 늘리고(예: `SPRING_DATA_REDIS_TIMEOUT=2s`), 정상 상태에서 캐시 미스가 잦으면 먼저 RTT를 본다.

**TLS 무응답 핸드셰이크에서 timeout이 동작하는가 → 부분 확인**
- `RedisTlsTimeoutTest`: `ssl.enabled=true` + 로컬 ServerSocket이 TCP 연결만 받고 아무것도 보내지 않음 → 명령이 **약 1.9초 만에 예외로 끝남**(이 테스트는 8초 미만, 상한 15초). 기본 60초 timeout이었다면 이 상한을 넘겨 실패했을 구조.
- **미검증**: ① 실제 TLS 서버가 핸드셰이크 도중 멈추는 경우(테스트 서버는 평문 무응답이라 TLS 레코드 일부를 보낸 뒤 멈추는 경우와 다를 수 있음) ② 실제 Upstash의 TLS/인증서 검증 ③ 연결 풀 상태에서의 동작.

## 9. spring.cache.type
- 현재 이미지/설정의 실제 값: **미설정**. 근거는 코드 확인이며 이미지를 띄워서 확인한 것은 아니다(Docker 불가): 로컬 yml에만 `cache.type: redis`가 있고(키 확인) `.dockerignore`가 `src/main/resources/application*`을 이미지에서 제외한다(25-b가 jar 안 설정 파일 0개로 확인).
- 그래도 동작이 바뀌지 않는 이유: `RedisCacheConfig`가 `@EnableCaching` + `RedisCacheManager` 빈을 **직접** 만든다. Boot의 `CacheAutoConfiguration`은 `CacheManager` 빈이 있으면 물러나므로 `spring.cache.type` 값과 무관하게 Redis 캐시가 쓰인다(25-b 표의 "영향 없음"과 같은 결론, 이번에 코드를 다시 읽어 확인). 따라서 **바꿀 필요 없음, 바꾸지 않았다.**
- Redis가 없거나 접속 불가할 때 (운영): `CacheErrorConfig`의 `LoggingCacheErrorHandler`가 get/put 예외를 WARN 로그로 삼켜 **캐시 미스처럼** 외부 API(TMDB)를 직접 호출한다(fail-open, 25-g 테스트로 검증). 연결 거부면 거의 즉시, 무응답이면 요청당 최대 약 2초 추가. `SPRING_DATA_REDIS_HOST`를 아예 안 주면 기본 `localhost:6379`로 가서 연결 거부 → 위와 같이 조용히 캐시 없이 동작한다. **즉 호스트 변수를 빠뜨려도 앱은 떠서 눈에 안 띄므로**, 배포 후 로그의 Redis WARN 유무를 확인해야 한다(§11).
- 테스트 환경은 `application-test.yml`의 `cache.type: simple`이고 Redis 캐시 경로 자체는 `RedisTimeoutFallbackTest` 등이 별도로 확인한다.

## 10. 배포 런북
> 모든 `<...>`는 사용자가 채운다. 값은 이 파일/레포에 쓰지 않는다. 명령 문법은 **실행해 보지 않았으므로(미검증)** 실행 전에 `gcloud ... --help`와 공식 문서로 대조할 것.
> 리전은 **asia-northeast1(도쿄)** 로 확정했다(Supabase 프로젝트가 도쿄, Upstash도 도쿄). 이 리전이 Cloud Run 리전 목록에 있는지는 **Cloud Run 리전 목록에서 확인**한다(미검증). GCP 프로젝트 ID만 `<PROJECT>`로 남겨 둔다.
> 이름 규칙(확정): Artifact Registry 저장소·이미지 `book-log`, 서비스 계정 `book-log-runtime`, 시크릿 `book-log-db-username`, `book-log-db-password`, `book-log-redis-password`, `book-log-kakao-key`, `book-log-tmdb-token`, `book-log-jwt-secret`.

### 10-0. 배포 전 체크리스트
- [ ] **25-f-1(스키마 대조 SQL 확장) 완료 전에는 첫 배포 금지.** 25-f-1은 SQL과 동기화 테스트를 만들었다(`22-perf/91-check-all-tables.sql`, `25-f-1-schema-check.md`). **남은 것은 사용자가 Supabase SQL Editor에서 91번을 하나씩 실행해 모든 "정상 결과"(대부분 0행)를 확인하는 일**이다. 하나라도 이상이면 배포 중단 (§7)
- [ ] asia-northeast1이 Cloud Run 리전 목록에 있는지 확인, `<PROJECT>`(GCP 프로젝트 ID) 확정
- [ ] Supabase 풀러 연결 한도 확인(최대 연결 10 안팎: maxScale 2 × 풀 5), 로컬이 쓰는 풀러 포트 확인(§6-1)
- [ ] Supabase DB가 깨어 있음(일시중지 아님)
- [ ] `./gradlew test` 최신 통과 (현재 330개, 실패 0, skipped 9)
- [ ] 환경변수 표(§4)의 필수 값이 모두 준비됨. 특히 `SPRING_DATA_REDIS_SSL_ENABLED=true`, `SPRING_PROFILES_ACTIVE=prod`
- [ ] 로컬 `application.yml`/`.env`에 있는 값을 레포나 YAML 파일에 복사하지 않았다 (env 파일은 `.gitignore` 대상 경로에)
- [ ] 첫 배포 공개 여부: 25-c 결정대로 바로 공개(`--allow-unauthenticated`), 25-e 점검 완료 상태
- [ ] 시크릿 6개 생성 + 서비스 계정 권한 부여 완료(§10-2). 빠뜨리면 리비전이 뜨지 않는다
- [ ] **메모**: 링크를 외부(면접관 등)에 공유하기 전에 `minScale`을 1로 바꿀지 다시 판단한다(§10-3)

### 10-1. 이미지 빌드/푸시
**로컬 Docker 의존 여부 확인**: 아래 두 방법 모두 **로컬 Docker가 필요 없다**(Cloud Build가 원격에서 `demo/Dockerfile`로 빌드). 로컬 Docker 데몬이 응답하지 않는 이 PC에서도 쓸 수 있는 경로다. 단 아래 명령은 **미검증이며 실행 전 gcloud 도움말/공식 문서로 대조**할 것.

A. 빌드만 하고 YAML로 배포 (기본안, §10-3과 이어짐)
```bash
# 저장소가 없다면 한 번만 (미검증)
gcloud artifacts repositories create book-log --repository-format=docker --location=asia-northeast1
gcloud builds submit ./demo --tag asia-northeast1-docker.pkg.dev/<PROJECT>/book-log/book-log:<TAG>
```
Dockerfile이 `demo/`에 있고 빌드 컨텍스트도 `demo/`이므로 위 경로. Artifact Registry 저장소는 미리 만들어 둔다. 필요한 API(Cloud Build, Artifact Registry, Cloud Run)는 프로젝트에서 활성화되어 있어야 한다.

B. 소스에서 바로 빌드+배포 (대안, 서비스가 이미 YAML로 만들어진 뒤의 코드 재배포용)
```bash
gcloud run deploy book-log --source ./demo --region asia-northeast1
```
- 소스 디렉터리에 Dockerfile이 있으면 그것으로 빌드하는 것으로 알고 있다(확인 필요). 이미지 저장소가 자동 생성되는지, 기존 서비스의 환경변수/시크릿/프로브 설정이 유지되는지는 **확인 필요**.
- **서비스를 처음 만들 때는 쓰지 않는다**: 환경변수/시크릿 없이 뜨면 prod가 CORS 누락과 API 키 누락으로 기동 실패하고 probe 설정도 못 넣는다. 첫 배포는 A + §10-3 YAML.

### 10-2. 서비스 계정, 시크릿 생성, 권한 부여 (확정: 전용 계정 + Secret Manager 참조)
값은 사용자 터미널에서 직접 입력(명령행 인자나 파일에 쓰지 않는다). 명령은 **미검증**이니 `--help`와 공식 문서로 대조.

**A. 런타임 서비스 계정 (이 서비스 전용, 최소 권한)** — 기본 Compute 계정은 권한이 넓어 쓰지 않는다
```bash
gcloud iam service-accounts create book-log-runtime --display-name="book-log runtime"
# 권한은 아래 B에서 시크릿 6개에 대한 secretAccessor만 부여한다. 프로젝트 전체 역할은 부여하지 않는다
```

**B. 시크릿 생성 + 권한**
```bash
# 0. API 활성화 (최초 1회)
gcloud services enable secretmanager.googleapis.com
# 1. 시크릿 6개 각각 반복: book-log-db-username, book-log-db-password, book-log-redis-password,
#    book-log-kakao-key, book-log-tmdb-token, book-log-jwt-secret
gcloud secrets create book-log-db-password --replication-policy=automatic
# 값 입력: 표준입력으로 (셸 기록에 값이 남지 않게)
gcloud secrets versions add book-log-db-password --data-file=-
# 2. 시크릿마다 전용 서비스 계정에만 읽기 권한 부여
gcloud secrets add-iam-policy-binding book-log-db-password \
  --member="serviceAccount:book-log-runtime@<PROJECT>.iam.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
# 3. JWT_SECRET은 YAML에서 버전을 고정하므로, 버전 번호를 확인해 둔다
gcloud secrets versions list book-log-jwt-secret
```
- 권한 부여가 빠졌거나 서비스 계정이 다르면 리비전 생성이 권한 오류로 실패한다(첫 배포의 흔한 실패 원인으로 알고 있음, 확인 필요).
- 시크릿 값 교체는 새 버전 추가 후 새 리비전 배포로 반영된다(`latest` 참조 기준). `book-log-jwt-secret`은 버전 고정이라 **교체 시 YAML의 버전 번호도 고쳐야** 한다.
- 서비스 계정에 Cloud Run 배포 권한을 따로 주는 것이 아니다: 이 계정은 컨테이너가 **실행될 때** 쓰는 신원이고, 배포하는 사용자의 권한(`iam.serviceAccountUser` 등)은 별개다(확인 필요).

### 10-3. 서비스 정의 (YAML) — `service.yaml` (레포 밖 또는 gitignore 위치에 둔다)
```yaml
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: book-log
spec:
  template:
    metadata:
      annotations:
        autoscaling.knative.dev/minScale: "0"      # 확정: 0으로 시작(비용 우선). 외부 공유 전에 1로 바꿀지 재판단 (§12-3)
        autoscaling.knative.dev/maxScale: "2"      # 확정: 2 x Hikari 풀 5 = 최대 연결 10 안팎. 풀러 한도를 넘으면 낮춘다 (§6-5)
        run.googleapis.com/startup-cpu-boost: "true"
    spec:
      serviceAccountName: book-log-runtime@<PROJECT>.iam.gserviceaccount.com
      containers:
        - image: asia-northeast1-docker.pkg.dev/<PROJECT>/book-log/book-log:<TAG>
          ports:
            - containerPort: 8080
          resources:
            limits:
              cpu: "1"
              memory: 1Gi        # JVM이 메모리의 75%를 쓰도록 되어 있음(Dockerfile). 실제 사용량은 미측정
          env:
            - { name: SPRING_PROFILES_ACTIVE, value: "prod" }
            - { name: SPRING_JPA_HIBERNATE_DDL_AUTO, value: "validate" }
            - { name: SPRING_JPA_SHOW_SQL, value: "false" }
            - { name: SPRINGDOC_API_DOCS_ENABLED, value: "false" }
            - { name: SPRINGDOC_SWAGGER_UI_ENABLED, value: "false" }
            - { name: APP_CORS_ALLOWED_ORIGINS, value: "http://localhost:3000" }   # 1단계 임시값. 프론트 주소 확정 후 프론트 origin으로 교체하고 localhost 제거 (§10-5)
            - { name: SPRING_DATASOURCE_URL, value: "jdbc:postgresql://<session-pooler-host>:5432/<db>" }   # session pooler(5432), 비밀번호 제외
            - { name: SPRING_DATA_REDIS_HOST, value: "<upstash-host>" }
            - { name: SPRING_DATA_REDIS_PORT, value: "<port>" }
            - { name: SPRING_DATA_REDIS_SSL_ENABLED, value: "true" }
            - { name: SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE, value: "5" }   # 이름 검증됨(HikariEnvBindingTest). maxScale 2 x 5
            - { name: JWT_ACCESSTOKENEXPIRATION, value: "<ms>" }
            - { name: JWT_REFRESHTOKENEXPIRATION, value: "<ms>" }
            # 시크릿 (Secret Manager 참조). 기본은 latest, JWT_SECRET만 버전 고정
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom: { secretKeyRef: { name: book-log-db-username, key: latest } }
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom: { secretKeyRef: { name: book-log-db-password, key: latest } }
            - name: SPRING_DATA_REDIS_PASSWORD
              valueFrom: { secretKeyRef: { name: book-log-redis-password, key: latest } }
            - name: API_KAKAOKEY
              valueFrom: { secretKeyRef: { name: book-log-kakao-key, key: latest } }
            - name: API_TMDBTOKEN
              valueFrom: { secretKeyRef: { name: book-log-tmdb-token, key: latest } }
            - name: JWT_SECRET
              valueFrom: { secretKeyRef: { name: book-log-jwt-secret, key: "1" } }   # 버전 고정. 첫 버전이면 1, 실제 번호는 `versions list`로 확인
          startupProbe:
            httpGet: { path: /actuator/health/readiness, port: 8080 }
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 24       # 넉넉한 출발값(period x threshold). 첫 배포 로그의 기동 시간을 보고 조정
          livenessProbe:             # DB 미포함(프로세스 상태만)
            httpGet: { path: /actuator/health/liveness, port: 8080 }
            periodSeconds: 30
            timeoutSeconds: 3
            failureThreshold: 3
```
- 시크릿은 Secret Manager 참조(`secretKeyRef`)로 확정이라 YAML에 값이 들어가지 않는다. `secretKeyRef`의 `key`에 버전(`latest` 또는 번호)을 쓰는 형식은 Cloud Run 문서로 대조(**미검증**).
- `JWT_SECRET` 교체 절차: 새 버전 추가 → YAML의 `key` 번호 수정 → 재배포. 교체하면 기존 토큰은 모두 무효가 된다.
- **minScale 메모**: 0이면 유휴 후 첫 요청에서 JVM 콜드 스타트 지연이 생긴다. 링크를 면접관 등 외부에 공유하기 전에 1로 바꿀지 다시 판단한다. 비용은 추측하지 않고 **콘솔 요금 계산기로 확인**한다.
- `APP_CORS_ALLOWED_ORIGINS`에 여러 origin을 쉼표로 넣어도 YAML 값이면 문제없다. 반면 `--set-env-vars`로 줄 때는 쉼표가 구분자와 충돌하니 이 방식(YAML)이나 대체 구분자 문법을 써야 한다.
- `gcloud run services replace`는 서비스 설정 전체를 YAML 기준으로 맞추므로 이후에도 이 파일을 원본으로 유지한다.

### 10-4. 배포
```bash
gcloud run services replace service.yaml --region asia-northeast1
# 공개 접근 (25-c 결정: 첫 배포는 공개). 25-e 점검 완료가 선행 조건
gcloud run services add-iam-policy-binding book-log --region asia-northeast1 \
  --member="allUsers" --role="roles/run.invoker"
```

### 10-5. 배포 순서 (프론트 호스팅과 무관한 일반형; 25-d 순환 해결)
| 단계 | 할 일 | 확인 방법 |
|---|---|---|
| 1 백엔드 | `APP_CORS_ALLOWED_ORIGINS`에 **유효한 placeholder `http://localhost:3000`** 을 넣어 배포(prod는 빈 값이면 기동 실패하므로) → 백엔드 URL 확정 | ① 리비전 Ready (startup probe 통과) ② `GET <백엔드URL>/actuator/health/liveness`, `/readiness` → 200 `{"status":"UP"}` ③ `GET <백엔드URL>/swagger-ui.html` → **401**, `/v3/api-docs` → 401 ④ `/actuator/env` → 401 ⑤ 로그에 `The following 1 profile is active: "prod"` |
| 2 프론트 (Vercel) | 백엔드 URL을 `NEXT_PUBLIC_API_URL`로 **빌드 시점에 주입**해 빌드/배포 → 프론트 URL 확정. Vercel에서는 **프로젝트의 빌드 환경변수**로 설정한다(설정 화면의 세부 조작은 Vercel 문서/대시보드 확인). 환경변수를 바꾸면 이전 빌드에는 반영되지 않으므로 다시 빌드해야 한다 | ① 값이 없으면 production 빌드가 **실패**해야 한다(25-d, 이미 확인된 동작) ② 빌드 산출물(번들)에 백엔드 URL이 들어 있고 `localhost:8080`이 없다(`grep`으로 `.next/static` 확인) ③ 브라우저 개발자 도구 Network에서 직접 호출 API(통계, 하이라이트, contents filter/check)가 백엔드 URL로 나간다 ④ 끝 슬래시 없는 값 사용 |
| 3 CORS 갱신 | **Vercel production 도메인만** 넣는다(Preview URL은 배포마다 바뀌므로 허용하지 않는다). 프론트 origin으로 `APP_CORS_ALLOWED_ORIGINS`를 **교체하고 `http://localhost:3000`은 제거**해 서비스 갱신(새 리비전). 로컬 개발이 운영 백엔드를 계속 쓸 일이 있을 때만 의도적으로 남긴다. 형식: `https://<프론트 도메인>` — 스킴 포함, 경로/쿼리 없음, 끝 `/` 없음, 여러 개면 쉼표, `*` 금지 | ① 리비전 Ready (형식이 틀리면 **기동 실패**가 정상 동작: 25-d-1) ② 터미널 확인(아래) ③ 브라우저에서 로그인 후 통계 화면이 CORS 오류 없이 뜸 ④ 서비스 설정에 `localhost`가 남아 있지 않은지 확인 |

CORS 터미널 확인 (preflight, 값은 placeholder):
```bash
curl -i -X OPTIONS "<백엔드URL>/api/<직접 호출 API 경로>" \
  -H "Origin: https://<프론트 도메인>" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: authorization"
# 기대: 200 + Access-Control-Allow-Origin: https://<프론트 도메인>
# 허용하지 않은 Origin이면 403 (CORS 헤더 없음)
```
- 프론트 도메인이 바뀌면(커스텀 도메인 도입 등) 3단계를 다시 한다. **커스텀 도메인은 첫 배포에 사용하지 않는다(확정)**: Cloud Run URL과 Vercel production URL로 먼저 연결을 검증하고, 도메인을 붙일 때 3단계를 다시 한다.
- Vercel Preview 배포에서는 백엔드 호출이 CORS로 막힌다(허용하지 않는 것이 의도). Preview에서 직접 호출 API를 확인할 수 없다는 점을 알고 있어야 한다. 프록시 경로(검색/인증)는 CORS와 무관하다.
- 로그인 정상 동작 확인: 프록시 경로(검색/인증)는 Next 서버가 백엔드를 부르므로 CORS와 무관하다(25-d). 직접 호출 쪽만 CORS 대상이다.

## 11. 25-h로 넘길 체크 목록 (이번에 수행하지 않음)
- 배포된 URL에서 `/swagger-ui.html`·`/v3/api-docs`·`/actuator/env` 401, liveness/readiness 200 재확인 (25-e 남은 TODO)
- 실제 Upstash RTT 측정, Redis timeout(1s) 적정성, Redis 연결 WARN 로그 유무, 같은 검색어 2회 호출 시 캐시 효과
- **Redis 캐시가 실제로 쓰이는지 확인**(로그의 Redis WARN 유무, 또는 같은 요청 두 번째 응답 시간). `SPRING_DATA_REDIS_HOST`를 빠뜨려도 정상 기동하기 때문(발견 8)
- Upstash 리전 지연 때문에 timeout 1s 오탐이 있는지 실제 값으로 판단
- JVM 기동 시간 측정 → startup probe period/`failureThreshold` 조정, 메모리 1Gi 적정성
- 외부 공유 전 `minScale` 0→1 재판단 (콜드 스타트 체감 + 콘솔 요금 계산기)
- 실제 Kakao/TMDB 호출 성공, 로그에 검색어/예외 메시지/비밀번호 형태 문자열(`Using generated security password`, 25-b 발견 3)이 남는지 (25-g 발견 4, 25-e 발견 5)
- DB 연결 수(Hikari × 인스턴스) 모니터링 — session pooler 한도 대비 확인, 부족하면 transaction pooler(6543 + `prepareThreshold=0`) 전환 검토
- 가입/로그인/refresh/하이라이트 CRUD 스모크, 401·403 응답에 CORS 헤더가 붙는지

## 12. 결정 현황
### 확정됨
| # | 결정 | 이유 / 트레이드오프 | 반영 위치 |
|---|---|---|---|
| 1 | 시크릿은 **Secret Manager 참조** (DB user/pw, Redis pw, Kakao, TMDB, JWT_SECRET) | 환경변수는 콘솔에 평문으로 보임. 단계가 늘고 서비스 계정 `secretAccessor` 필요 | §4, §10-2, §10-3 |
| 2 | `SPRINGDOC_*_ENABLED=false`는 **service YAML에만** | prod 프로파일이 이미 Swagger를 끄고 `ProdExposureTest`가 보장. 설정 출처를 늘리지 않음 | §3-3, §10-3 |
| 3 | **minScale 0**으로 시작 | 비용 우선. 첫 요청 콜드 스타트 지연. 외부 공유 전 1로 바꿀지 재판단 (비용은 콘솔 요금 계산기로 확인) | §10-0, §10-3, §11 |
| 4 | DB는 **session pooler(5432)** 로 시작 | direct는 IPv6 문제 가능(Supabase 문서로 확인 필요), session pooler는 prepared statement 문제 없음. transaction pooler는 후속 옵션 | §6, §10-3 |
| 5 | **Redis timeout 1s 유지** | 조정 근거 없음. Upstash 리전 지연 오탐은 25-h에서 판단 | §3-5, §8, §11 |
| 6 | 1단계 임시 CORS origin = **`http://localhost:3000`** | prod는 빈 값이면 기동 실패. 프론트 주소 확정 후 교체하고 localhost 제거 | §10-3, §10-5 |
| 7 | 발견 사항: 스키마 SQL 확장은 **25-f-1로 분리, 완료 전 첫 배포 금지** / Redis 캐시 사용 확인은 25-h / startup probe(readiness, DB 포함)는 **유지**, probe 값은 넉넉히 | 각각 §7, §10-0 / §11 / §5, §10-3 | |
| 8 | **리전 asia-northeast1(도쿄)**, Upstash Redis도 도쿄 (3차) | Supabase 프로젝트가 도쿄. DB·Redis·서버 간 지연 최소화. Cloud Run 리전 목록에 있는지는 확인 필요(미검증) | §8, §10 |
| 9 | **maxScale 2, Hikari 풀 5** (3차) | session pooler는 인스턴스 × 풀 크기만큼 연결 점유 → 최대 10 안팎. 풀러 한도를 넘으면 낮춤. 풀 5는 측정값이 아닌 출발값 | §6-5, §10-3 |
| 10 | Hikari 환경변수 이름 `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` **검증됨** (3차) | `HikariEnvBindingTest`: 실제 풀 크기 5, 잘못된 이름이면 실패 확인 | §6-5, §15 |
| 11 | 시크릿 버전: 기본 `latest`, **JWT_SECRET만 버전 고정** (3차) | 교체 시 전 토큰 무효. 트레이드오프: 교체 때 YAML 버전 번호 수정+재배포. 문법은 미검증 | §4, §10-3 |
| 12 | **전용 서비스 계정 `book-log-runtime`**, 시크릿 6개 `secretAccessor`만 (3차) | 기본 Compute 계정은 권한이 넓음(최소 권한) | §10-2 |
| 13 | 이름 규칙: 저장소·이미지 `book-log`, 시크릿 `book-log-db-username/-db-password/-redis-password/-kakao-key/-tmdb-token/-jwt-secret` (3차) | 일관성 | §10 머리말 |
| 14 | **프론트 호스팅 Vercel 확정** (3차) | decisions.md 2026-11-04와 일치(모순 해소). `NEXT_PUBLIC_API_URL`은 Vercel 빌드 환경변수, CORS에는 production 도메인만(Preview URL 불허). Vercel 화면 조작은 문서 확인 | §10-5 |
| 15 | **커스텀 도메인은 첫 배포에 쓰지 않음** (3차) | Cloud Run URL + Vercel production URL로 먼저 연결 검증. 도메인을 붙이면 3단계 재수행 | §10-5 |
| 16 | **25-f-1은 다음 세션에 바로 진행** (3차) | 첫 배포의 선행 조건 | §7, §10-0 |

### 미확정 (사용자가 정해야/확인해야 함)
1. **GCP 프로젝트 ID** (`<PROJECT>`로 유지, 레포에 기록하지 않음)
2. **Supabase 풀러 실제 연결 한도**: 대시보드에서 확인. 최대 연결 10(2 × 5)이 한도 안인지 확인하고, 넘으면 풀 크기/maxScale을  -> 한도 15 확인
3. **로컬이 쓰는 풀러 포트**: 로컬 설정이 transaction pooler(6543)인지 session(5432)인지 확인(운영은 5432로 확정이라 URL의 `prepareThreshold=0` 유무가 로컬과 달라질 수 있음)
4. **JWT_SECRET 실제 버전 번호**(첫 버전이면 1): `versions list`로 확인 후 YAML에 반영
5. asia-northeast1이 Cloud Run 리전 목록에 있는지, 서비스 계정 배포 권한 형태 등 **gcloud 명령/YAML 문법 전반의 공식 문서 대조**(미검증)

## 13. 막힌 부분과 해결
- **Docker 데몬 없음**: `docker version`이 npipe 연결 실패(25-b 때는 응답했음). 이미지 빌드/실행은 우회하지 않고 "검증 못 함"으로 기록했다. Dockerfile 변경은 텍스트 테스트와 앱 기동 테스트로만 보강했다.
- **`cleanTest`가 `Unable to delete directory`로 실패**: 내 셸의 작업 디렉터리가 `build/test-results/test` 안이라 Windows가 폴더 삭제를 막았다(25-e의 "파일 잠금"과 같은 유형). 작업 디렉터리를 `demo/`로 옮겨 재실행해 통과.
- **prod 기동 실패 테스트의 비용**: 실제 앱을 띄우다 실패하는 테스트라 약 20초 걸린다(`SecurityConfig`의 CORS 빈 생성 전까지 JPA 등이 올라감). 전체 스위트 시간이 늘어나는 점은 발견 5번에 기록.
- gcloud 플래그/YAML 문법을 **실행하지 못해 검증 불가**: 프로브 gcloud 플래그는 확신이 없어 YAML을 기본으로 했고, 문서 확인을 런북에 명시했다.

## 14. 발견한 문제 (고치지 않음)
1. **Dockerfile ENV prod의 부작용**: 25-b의 로컬 `docker run` 스모크 절차는 이제 `APP_CORS_ALLOWED_ORIGINS`를 주거나 프로파일을 덮어써야 한다. 25-b 파일은 수정하지 않았다.
2. **`90-check-real-schema.sql`이 `content`만 본다.** `members` 등 다른 테이블의 컬럼·제약·유니크(`uk_members_email`)는 이 파일로 확인되지 않는다. `validate`는 인덱스/제약도 안 본다. **-> 25-f-1로 분리하기로 확정, 이 task에서는 고치지 않았고 25-f-1 완료 전 첫 배포 금지(§10-0).**
3. **풀러 충돌 가능성 미조사**: session pooler 기준으로는 prepared statement 문제가 없다고 알고 있으나 미검증. Hikari 설정이 없고(기본 10, session pooler에서는 인스턴스 x 풀 크기만큼 연결 점유), transaction pooler에서 문제가 되는 세션 상태 사용 여부(`SET`, advisory lock 등)는 전수 확인하지 않았다. prepared statement 문제는 `prepareThreshold=0`로 대응하는 것으로 알려져 있으나 이번에 검증하지 않았다.
4. **startup이 DB에 의존**: Supabase 일시중지/장애 시 새 리비전이 Ready가 되지 못한다. **의도된 동작으로 유지 확정**(§5). 배포 실패 원인이 DB일 수 있음을 알아야 하고, 기존 리비전은 유지된다.
5. `ProdProfileDefaultTest`의 기동 실패 테스트가 약 20초로 느리다. `SecurityConfig`의 CORS 검증이 컨텍스트 앞단에서 일어나게 하면 줄일 수 있으나 범위 밖.
6. Cloud Run의 환경변수 덮어쓰기로 `SPRING_PROFILES_ACTIVE`가 prod가 아니게 되는 것은 어떤 테스트로도 막을 수 없다. 체크리스트와 25-h 스모크에 의존.
7. `JWT_SECRET`을 교체하면 모든 사용자의 토큰이 무효가 된다(서버가 refresh를 저장하지 않음, 14번 결정). 시크릿 `latest` 참조 + 실수로 새 버전 추가 시 같은 일이 생긴다.
8. `SPRING_DATA_REDIS_HOST`를 빠뜨려도 앱이 정상 기동해(localhost로 가서 fail-open) 눈에 띄지 않는다(**-> 25-h 체크 목록에 "Redis 캐시가 실제로 쓰이는지 확인" 추가함**) — 25-a의 "필수 누락은 기동에서 드러낸다" 원칙과 다른 유일한 예외. Redis는 캐시라 의도된 설계지만 운영 실수가 조용히 묻힌다.
9. (2차) 1단계 임시 CORS 값 `http://localhost:3000`을 3단계에서 지우지 않으면 운영 백엔드가 로컬 개발 서버 origin을 계속 허용한다. 인증은 Authorization 헤더라 위험은 작지만 §10-5 3단계 확인 항목 ④로 챙긴다.
10. (2차) `gcloud run deploy --source`의 Dockerfile 사용/기존 설정 유지 여부, session pooler의 `prepareThreshold` 불필요 여부, Hikari 환경변수 이름, startup probe 허용 상한은 모두 **미검증**(실행 전 도움말·공식 문서 대조).
11. 이월: root `/actuator/health` 공개, catch-all 500, `frameOptions` 비활성(25-e), 검색어 로깅(25-g), `Using generated security password` 로그(25-b), Testcontainers 9개 skip.
12. (3차) Hikari 풀 5는 측정값이 아니라 연결 수 상한(10)에서 역산한 출발값이다. 동시 요청이 풀 크기를 넘으면 풀 대기가 생긴다(25-h 확인). `..._MAXIMUMPOOLSIZE`처럼 밑줄 없는 이름은 시도하지 않았다.
13. (3차) maxScale 2여도 새 리비전 배포 중 구/신 리비전이 잠시 겹치면 연결 수가 순간적으로 늘 수 있는지는 확인하지 않았다(미확인). 한도에 여유가 없다면 문제가 될 수 있다.
14. (3차) JWT_SECRET 버전 고정: 그 버전을 비활성화/삭제하거나 번호를 틀리면 리비전이 뜨지 않을 수 있다(미확인). 교체 절차는 §10-3.
15. (3차) Vercel Preview 배포는 CORS 때문에 백엔드 직접 호출 API를 쓸 수 없다(의도). Preview에서 화면 검증을 하려면 별도 결정이 필요하다.

## 15. 테스트 결과 (실행한 것만)
- 신규 3개만 먼저 실행: `ProdProfileDefaultTest` 2개, `RedisTlsTimeoutTest` 1개 모두 통과 (XML 확인, TLS 테스트 약 1.9초).
- 전체 `./gradlew cleanTest test`(demo/): **BUILD SUCCESSFUL, 330개 실행, 실패 0, 에러 0, skipped 9** (이전 327 + 신규 3). skipped 9는 이전과 같은 `ContentRepositoryPostgresTest`(Testcontainers가 Docker를 인식 못 함 — 이번에는 Docker 데몬 자체가 응답하지 않음). 통과가 확인되지 않은 상태 그대로다.
- (3차) `HikariEnvBindingTest` 1개: 올바른 이름에서 통과. **이름을 일부러 틀리게 바꿔 보면 실패**("... 최대 풀 크기 5가 된다 FAILED")하고 원복 후 다시 통과하는 것을 확인했다.
- (3차) 전체 `./gradlew cleanTest test`(demo/): **BUILD SUCCESSFUL, 331개 실행, 실패 0, 에러 0, skipped 9** (이전 330 + 신규 1). skipped 9는 이전과 같은 `ContentRepositoryPostgresTest`(통과 미확인 상태 그대로).
- 실행하지 않은 것: `docker build`/컨테이너 기동(데몬 없음), gcloud 명령 전부, 실제 Supabase/Upstash/Kakao/TMDB 접속, CI(application.yml 없는 환경)에서의 신규 테스트, 프론트 빌드.
- 커밋하지 않음.

## 16. 정리 (면접/트러블슈팅용)
1. **문제 상황**: 이미지가 `SPRING_PROFILES_ACTIVE`를 정하지 않아 배포 설정에서 빠뜨리면 운영에서 Swagger가 열리고 CORS가 localhost 기본값으로 뜰 수 있었다. 헬스체크 경로, 시크릿/일반 변수 분류, DB 풀러, 스키마 대조, 배포 순서는 파일마다 흩어져 있어 첫 배포 때 빠뜨리기 쉬웠다.
2. **고려한 선택지**: (프로파일) ① 배포 환경변수에만 둠 ② Dockerfile ENV 기본값(채택) ③ `application-prod.yml`(이미지에 안 들어가 불가). (이중 방어) ① Dockerfile에도 springdoc 변수 ② 배포 환경변수에만(채택) ③ 없음. (시크릿) ① Secret Manager 참조(채택) ② 환경변수 직접. (DB) ① direct ② session pooler 5432(채택) ③ transaction pooler 6543(후속 옵션).
3. **선택 이유**: 프로파일을 잃으면 보안 설정이 열리는 쪽으로 깨지므로 "이미지가 안전한 기본값을 가진다"가 사람의 체크리스트보다 강하다. 트레이드오프: 로컬 `docker run`도 prod가 되고, 환경변수로 덮어쓰면 다시 열린다(테스트로 못 막음). springdoc 이중 방어는 얕은 보험이라 비용이 거의 없는 배포 설정에만 둔다. 시크릿은 값이 서비스 설정에 남지 않는 쪽을 기본안으로 했고, 권한 설정이 한 단계 늘어나는 비용이 있다.
4. **막혔던 부분과 해결**: 로컬 Docker 데몬이 없어 이미지 검증이 불가능했다 → 우회하지 않고 텍스트/기동 테스트와 "미검증" 기록으로 대체. "TLS 무응답에서 timeout이 도는가"는 로컬 무응답 서버 테스트로 부분 확인(약 1.9초에 예외)하고, 실제 TLS 핸드셰이크 중단과 Upstash는 미검증으로 남겼다.

## 17. 복습 질문
<details><summary>Q1. Dockerfile에 `ENV SPRING_PROFILES_ACTIVE=prod`를 넣은 이유는? 이 값을 빠뜨렸을 때 CORS와 Swagger는 각각 어느 방향(안전/열림)으로 깨지고, 그래도 남는 구멍은 무엇인가?</summary>

(여기에 답)

</details>

<details><summary>Q2. Cloud Run probe에 `/actuator/health`가 아니라 liveness/readiness를 쓰는 이유는? startup probe에 readiness(DB 포함)를 쓰면 Supabase가 응답하지 않을 때 배포에서 어떤 일이 생기나?</summary>

(여기에 답)

</details>

<details><summary>Q3. (기존 코드 연결, 25-a/25-g) 운영에서 Redis 호스트 환경변수를 빠뜨리면 앱은 어떻게 동작하나? 25-a의 "필수 설정 누락은 기동 실패" 원칙과 어떻게 다르며, 25-g의 timeout·CacheErrorConfig는 이 상황에서 각각 어떤 역할을 하나?</summary>

(여기에 답)

</details>
