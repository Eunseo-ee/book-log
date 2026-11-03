# 24-2. 배포 전 점검 (read-only 감사)

- 번호: 24, 24-1 다음이라 어긋나지 않음. task 25는 별도 세션(Docker 배포)이라 쓰지 않음. 참고: `tasks/15-*.md`는 처음부터 없음(로드맵에도 15 없음).
- 성격: 조사와 보고만. 코드·설정·워크플로·기존 task 파일은 수정하지 않았고 커밋하지 않았다. 시크릿 값은 어디에도 쓰지 않았다(이름과 존재 여부만).
- 실행한 것: `./gradlew test`(로컬), git 이력 검색(값 자체는 출력 안 함), 코드/설정 읽기. 실행하지 않은 것: 앱 기동, Docker 빌드, Supabase/Upstash/GitHub 웹 확인(`gh` CLI도 없음).

## 확인한 범위
- `.claude/CLAUDE.md`, `decisions.md`, `backlog.md`, `inbox.md`, tasks 전체(특히 발견/TODO 섹션: 16~24-1)
- `demo/src/main` 전체의 설정·보안·예외·외부 API·캐시 코드, `demo/build.gradle`, `.gitignore`, `.github/workflows/ci.yml`
- `frontend/lib/api.ts`, `next.config.js`, `.env.local.example`
- `application.yml` / `application-api-key.yml`은 **키 이름과 존재 여부만** 사용
- `./gradlew test`(실제 실행): BUILD SUCCESSFUL, XML 33개 클래스, **223개 실행, 실패 0, 에러 0, skipped 9**. skipped 9는 전부 `ContentRepositoryPostgresTest`(이 PC에서 Docker 미연결). 따라서 이 PC에서는 PostgreSQL 쿼리 검증이 돌지 않았다.

---

## A. 배포 전 필수 (task 25에서 처리)

### A1. 시크릿: 이력·이미지·유출 가능성
- 근거: `.gitignore`에 `application.yml`, `application-api-key.yml` 등재(현재 추적 안 됨, `git ls-files`로 확인). 현재 로컬 파일의 5개 값(Kakao 키, TMDB 토큰, JWT 시크릿, DB 비밀번호, Redis 비밀번호)을 `git log --all -S`로 검색 → **어느 커밋에도 없음(0건, 로컬 ref + 원격 추적 브랜치 기준)**.
- 그러나 과거 커밋 `473ed18`, `77528bd`, `5163c76`, `1715472`(2026-04-13, 2026-05-05, 2026-10-15)에 시크릿 형태의 줄(`kakao-key:`, JWT 형태 토큰, `password: "`)이 있다. 이전 값이라 현재 값과 다르지만 **그 값들이 폐기(재발급)됐는지는 task 파일에 근거가 없다.** 로드맵의 "옛 시크릿 삭제 완료"는 파일 삭제를 뜻하고 키 회전과는 다르다.
- 원격 `origin/feat/security`, `feature/frontend`, `fix/calendar`, `feat/search-ui`에 위 커밋이 포함된다(`git branch -r --contains`). 레포 공개 여부는 확인하지 못했다.
- 조치(제안): 옛 Kakao/TMDB/DB/Redis 값이 모두 폐기됐는지 각 콘솔에서 확인. 운영용 값은 로컬 개발 값과 **새로 분리해 발급**(아래 A2). 레포가 public이면 이력 정리(filter-repo 등)는 별도 판단.
- **이번 감사 중 사고(내 실수):** 설정 파일 값을 가리려던 셸 스크립트의 마스킹이 동작하지 않아, 현재 5개 값이 이 세션의 도구 출력(대화 기록)에 그대로 찍혔다. 파일/task에는 쓰지 않았다. 이 세션 기록을 공유하지 말고, 현재 로컬 값을 운영에 재사용하지 않는 것을 권한다(재발급이 가장 안전).

### A2. 배포 환경에는 application.yml이 없다 → 모든 설정을 환경변수로
- 근거: `application.yml`(gitignore)이 `spring.profiles.include: api-key`로 `application-api-key.yml`을 끌어온다. 이미지/호스트에는 두 파일이 없으므로 아래가 전부 환경변수여야 한다.
- 필요한 설정과 바인딩(규칙 기반 설명이며 **기동해서 확인하지 않았다**):

| 용도 | 프로퍼티 | 환경변수 | 바인딩 방식 |
|---|---|---|---|
| DB URL/계정/비번 | `spring.datasource.url/username/password` | `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | Boot 자동구성 |
| JPA DDL | `spring.jpa.hibernate.ddl-auto` | `SPRING_JPA_HIBERNATE_DDL_AUTO` | 같음 |
| Redis | `spring.data.redis.host/port/password/ssl.enabled` | `SPRING_DATA_REDIS_HOST/PORT/PASSWORD`, `SPRING_DATA_REDIS_SSL_ENABLED` | 같음 |
| 캐시 타입 | `spring.cache.type` | (필요 없음: `RedisCacheConfig`가 CacheManager를 직접 만든다) | - |
| JWT | `jwt.secret`(BASE64), `access-token-expiration`, `refresh-token-expiration` | `JWT_SECRET`, `JWT_ACCESSTOKENEXPIRATION`, `JWT_REFRESHTOKENEXPIRATION` | `JwtProperties` `@ConfigurationProperties`: 기본값 없음, 없으면 기동 실패(좋음) |
| Kakao/TMDB | `api.kakao-key`, `api.tmdb-token` | `API_KAKAOKEY`, `API_TMDBTOKEN` | `ApiConfig` `@ConfigurationProperties` (CI와 동일) |
| CORS origin | `app.cors.allowed-origins` | `APP_CORS_ALLOWED_ORIGINS` | `SecurityConfig`의 `@Value`. 쉼표 구분, 기본 `http://localhost:3000` |
| 프론트 | `NEXT_PUBLIC_API_URL` | 빌드 시점 값 | A6 참고 |

- **위험 1 (조용한 H2 폴백):** `build.gradle`에 `runtimeOnly 'com.h2database:h2'`가 운영 클래스패스에 있다. `SPRING_DATASOURCE_URL`을 빠뜨리거나 오타 내면 Boot가 인메모리 H2로 **에러 없이 기동**할 수 있다(Boot 자동구성의 일반 동작, 실행 확인 안 함). 반대로 PostgreSQL URL만 주고 ddl-auto를 안 주면 기본값은 `none`이라 스키마를 만들지 않는다. 조치: H2를 `testRuntimeOnly`로 옮기거나, 운영 프로파일에서 URL 필수 검증.
- **위험 2 (API 키 fail-fast 없음):** `JwtProperties`와 달리 `ApiConfig`에는 검증이 없다. `API_KAKAOKEY`/`API_TMDBTOKEN`이 비면 헤더가 `KakaoAK null` / `Bearer null`로 나가고, TMDB는 예외를 삼켜 **빈 검색 결과**(`TmdbSearchService` catch), Kakao는 500이 된다. 기동은 정상이라 배포 직후 못 알아챈다.
- **위험 3 (시크릿이 이미지/JAR에 구워짐):** `src/main/resources`의 gitignore 파일은 로컬 디스크에 존재하므로, 로컬에서 `bootJar`를 만들거나 `COPY demo/ ...`로 Docker 컨텍스트를 만들면 두 파일이 JAR/레이어에 포함된다. 조치: Dockerfile을 멀티스테이지 + `.dockerignore`에 `**/application.yml`, `**/application-api-key.yml`, `.env*` 추가, 또는 CI/클린 체크아웃에서만 빌드. 현재 `.dockerignore`/Dockerfile 없음(task 25 범위).
- 참고: task 24 복습 Q2는 `API_KAKAO_KEY`가 "안 된다"는 전제로 보이는데, Boot의 환경변수 매퍼는 두 형태를 모두 받아들이는 것으로 알고 있다. 실행해 확인하지 않았으므로 **답을 단정하지 말고 직접 확인할 것**.

### A3. `ddl-auto: update`로 운영 스키마 관리
- 근거: `application.yml`의 `ddl-auto: update`("개발 초기용" 주석). task 16(a-2: NOT NULL 추가 실패가 로그만 남고 앱은 뜸), 21-1(제약 생성 실패가 조용히 넘어갈 수 있음), 22(발견 7: 마이그레이션 도구 검토).
- 상태: task 22에서 Supabase에 유니크 제약·인덱스·FK가 실제로 존재하고 중복 행이 없음을 확인했다(발견 4 "해소"). `uk_members_email`(task 21-1)이 운영 DB에 있는지는 task 파일에 기록이 없다 → 사용자 확인 필요(C 목록).
- 조치(제안): 배포 시 `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`(또는 none)로 고정. 스키마 변경은 수동 SQL → 장기적으로 Flyway. `show-sql: true`도 함께 끈다(A7).

### A4. 타임존 (컨테이너는 보통 UTC)
- 근거: `ContentService.java:61` `requestDto.getViewDate().isAfter(LocalDate.now())`, `StatisticsController.java:35-36` 기본 연/월. 둘 다 서버 기본 타임존을 쓴다. 타임존 설정(`TimeZone`, `jdbc.time_zone`, `Clock`)은 코드/설정 어디에도 없다.
- 영향(코드상): 서버가 UTC면 한국 00:00~09:00에 "오늘" 날짜로 기록을 저장할 때 서버는 어제 날짜라 "미래 날짜"로 거절될 수 있고, 통계 기본 월도 월초/월말에 하루 어긋날 수 있다. 실행해 재현하지 않았다.
- 조치(제안): `TZ=Asia/Seoul` 환경변수 또는 `Clock` 주입 + `ZoneId.of("Asia/Seoul")`. 시간대 결정 후 decisions에 기록.

### A5. CORS origin과 프론트의 API 호출 경로
- 근거: `SecurityConfig`의 허용 origin 기본값이 `http://localhost:3000`뿐이라 배포 주소를 `APP_CORS_ALLOWED_ORIGINS`로 넣어야 한다(task 8, 19에서 설계). 허용 메서드는 GET/POST/PUT/DELETE/OPTIONS, 헤더 `*`, `allowCredentials` 없음(Authorization 헤더 방식이라 충분).
- 프론트는 두 갈래다(backlog에도 있음): 검색/캘린더/콘텐츠 저장은 `PROXY_BASE_URL=''`(Next rewrites), 통계/필터/하이라이트/인증은 `BASE_URL`로 백엔드 직접 호출(`frontend/lib/api.ts:17`). 직접 호출은 CORS가 필요하고 `NEXT_PUBLIC_API_URL`이 **빌드 시점에 번들에 박히며** 없으면 `http://localhost:8080`으로 폴백한다 → 배포 빌드에서 이 변수를 빠뜨리면 사용자 브라우저가 자기 localhost를 호출한다. `next.config.js`의 rewrites 대상도 같은 변수(빌드/서버 시작 시점)이고 같은 폴백을 쓴다.
- 조치(제안): 배포 프론트 빌드에 `NEXT_PUBLIC_API_URL` 설정을 체크리스트화. 호출 경로를 한쪽으로 통일할지 결정(통일하면 운영에서는 CORS 의존이 줄어든다). `lib/api.ts` 주석("@CrossOrigin은 StatisticsController에만")은 이미 사실과 다름(backlog).

---

## B. 권장 (배포 직후 또는 가능하면 같이)

| # | 항목 | 근거 | 제안 조치 |
|---|---|---|---|
| B1 | Swagger UI/`v3/api-docs`가 운영에서 공개 | `SecurityConfig.java:54` permitAll, springdoc 의존성 | 운영 프로파일에서 `springdoc.api-docs.enabled=false`, `swagger-ui.enabled=false` 또는 permitAll 제거 |
| B2 | 로그인/가입/refresh에 횟수 제한 없음 | backlog 13·14, `/api/auth/**` permitAll, BCrypt는 CPU 비용이 큼 | 최소: 플랫폼/프록시 레벨 rate limit. 코드 레벨(IP/아이디 기준)은 Phase 12 항목과 연결 |
| B3 | Kakao 호출에 예외 처리 없음 | `KakaoSearchService.search`에 try/catch 없음(TMDB는 있음), `body.getMeta()` NPE 가능. `SearchService.searchAll`은 provider별 보호가 없어 Kakao 한 번 실패하면 **통합 검색(ALL) 전체가 500** | Kakao도 TMDB와 같은 방식(로그 + 빈 응답), 또는 searchAll에서 provider별 격리. 기존 테스트 `KakaoSearchServiceTest` 확인 필요 |
| B4 | 외부 URL에 검색어를 인코딩 없이 연결 | `KakaoSearchService`, `TmdbSearchService`: `"...?query=" + query`를 `exchange(String ...)`에 전달 → URI 템플릿으로 해석되어 `{`/`&`/`#` 등이 포함된 검색어가 깨지거나 파라미터가 섞일 수 있다(코드상, 실행 확인 안 함). 프론트 `check` API도 같은 문제(task 21 발견 7) | `UriComponentsBuilder` 사용 |
| B5 | Redis 장애 시 지연 | `CacheErrorConfig`가 예외는 삼키지만, Redis 타임아웃은 설정이 없다(`spring.data.redis.timeout`, `connect-timeout` 미설정 → Lettuce 기본값이 길다고 알고 있음). 장애 시 검색마다 타임아웃만큼 기다린 뒤 폴백할 수 있다(추정, 미실행). `CacheFailureFallbackTest`는 예외 폴백만 보는 것으로 보임 | `spring.data.redis.timeout`/`connect-timeout`을 짧게(예: 1~2초) 환경변수로. Upstash 무료 한도 초과/중단 시 동작 확인 |
| B6 | `GlobalExceptionHandler` catch-all이 클라이언트 오류를 500으로 바꿈 + `printStackTrace` | `GlobalExceptionHandler.java:96`. `?category=잘못된값`(`MethodArgumentTypeMismatchException`), 본문 누락/깨진 JSON(`HttpMessageNotReadableException`), 없는 경로(`NoResourceFoundException`)가 400/404가 아니라 500 + 스택트레이스로 간다(backlog 14, 미확인이었음; 핸들러는 그대로라 여전히 유효) | 해당 예외를 400/404로 매핑, `printStackTrace` → `log.error(..., e)`. 500 응답 메시지는 일반 문구라 내부 정보 노출은 없음(확인) |
| B7 | `show-sql: true`, `format_sql: true` | `application.yml` | 운영에서 끈다(로그 폭증·성능). 환경변수 `SPRING_JPA_SHOW_SQL=false` |
| B8 | 헬스체크가 Redis/DB 상태에 묶일 수 있음 | `management` 설정 없음(task 19). Boot 기본으로 `/actuator/health`에 db·redis 인디케이터가 들어간다고 알고 있어, Upstash 장애가 헬스 DOWN → 플랫폼 재시작으로 이어질 수 있다(추정). `permitAll`은 `/actuator/health/**`뿐이고 다른 actuator는 인증 필요(확인) | `management.endpoints.web.exposure.include=health` 명시, 필요하면 `management.health.redis.enabled=false` 또는 liveness/readiness 분리. 플랫폼 헬스체크 경로 결정 |
| B9 | `ApiConfig` 키 누락 fail-fast | A2 위험 2 | 기동 시 검증(JwtProperties처럼) 또는 `@Validated` |
| B10 | CI가 보증하지 못하는 것 | `ci.yml`: ① 시크릿 `KAKAO_KEY`/`TMDB_TOKEN`은 주입하지만 테스트가 외부 API를 모킹해 **실사용 미검증** ② `push`(전 브랜치) + `pull_request: [main]` 둘 다 → 같은 레포 브랜치 PR은 2번 실행(concurrency 그룹이 ref별이라 합쳐지지 않음) ③ 운영 프로파일(PostgreSQL + `ddl-auto` 등)로 앱을 기동하는 검증 없음 ④ `chmod +x gradlew` 스텝은 이제 불필요(`git ls-files -s`가 100755; task 24는 100644라 적음) | 배포 후 스모크 테스트(헬스 + 로그인 + 검색 1건)를 task 25의 확인 항목으로. 나머지는 나중에 |
| B11 | 로컬/CI와 다른 PostgreSQL 전용 쿼리의 CI 검증 | task 24·24-1: CI에서 `ContentRepositoryPostgresTest` 9개가 실행되는지 task 파일에는 **"미확인"**으로만 남아 있다. 로드맵은 "CI 통과, skipped 0"으로 표기 | 사용자가 Actions Summary로 확인(C 목록) 후 task 24-1에 결과 기록 |

## C. 나중에 (현재 배포를 막지 않음)
- refresh 토큰 저장/회전/폐기, 로그아웃 엔드포인트(task 14, 21) · httpOnly 쿠키 전환(backlog) · localStorage 의 refresh XSS 위험(decisions 47)
- 403/404 구분으로 id 열거(task 18) · 이메일 중복 409의 계정 열거(21-1) · 아이디 대소문자 정책
- FK 위반 500(삭제된 회원의 유효 토큰) · 회원 삭제 정책(탈퇴 기능 없음) · null externalId 중복 판정 불일치(task 22 발견 2, 3) · 통계 6쿼리 스냅샷 불일치
- 하이라이트 update/delete 쿼리 2회(task 18) · 통계 쿼리 통합(task 22)
- 위조 토큰 요청마다 WARN 로그(`JwtTokenProvider.fail`): 토큰 값은 안 남는 것을 확인(좋음), 볼륨만 문제
- 잔재 파일: `demo/src/test/java/book_log/demo/DemoApplication.java`(main과 FQCN 동일 — 테스트 클래스패스가 먼저라 `@SpringBootTest`가 이쪽을 설정 클래스로 잡을 수 있고, 이 파일에는 `@EnableJpaAuditing`/`@EnableConfigurationProperties(ApiConfig)`가 없다. 감사(auditing) 동작을 테스트가 보증하지 못할 가능성이 있으나 **실행해 확인하지 않음**), 빈 `injectMocks.java`, `frontend/tsconfig.tsbuildinfo`(빌드 산출물이 추적됨, `.gitignore`에 없음), `AnimeSearchService.java`(전체 주석 처리된 파일)
- `AnimeSearchService`·AniList는 비활성. 프론트 `next.config.js`의 `images.remotePatterns`에 anilist 호스트가 남아 있음(무해)
- Docker 29 + Testcontainers 1.20.4 비호환(task 20 발견 2): 이 PC에서는 여전히 skip 9(이번에 확인). CI(ubuntu)에서는 별개 문제이므로 B11로 확인
- Redis 값 직렬화가 `GenericJackson2JsonRedisSerializer`(기본 타이핑): Redis 접근이 탈취되면 역직렬화 위험이 있는 구성이다. 캐시 대상이 검색 결과뿐이고 Upstash 인증이 있으므로 낮음

---

## D. task 25 작은 task 후보 (한 세션에 하나씩)
1. **25-a 운영 설정 주입 정리**: 환경변수 표(A2) 확정, `.env.example`(값 없이 이름만) 작성, H2를 test 전용으로, `ApiConfig` fail-fast, 운영은 `ddl-auto=validate`, `show-sql=false`. (A2, A3, B7, B9)
2. **25-b Dockerfile + .dockerignore**: 멀티스테이지, 클린 컨텍스트, 시크릿이 이미지에 안 들어가는지 `docker run --rm image ls`/JAR 목록으로 확인, `TZ=Asia/Seoul`. (A2 위험 3, A4 일부)
3. **25-c 타임존 일관화**: `LocalDate.now()` 2곳을 `Clock`/`ZoneId` 기반으로, 경계 테스트(KST 새벽). (A4)
4. **25-d CORS/프론트 연결**: 배포 주소를 `APP_CORS_ALLOWED_ORIGINS`로, 프론트 빌드 환경변수 체크, 호출 경로 통일 여부 결정. (A5)
5. **25-e 운영 노출 정리**: Swagger 비활성, actuator exposure 고정, 헬스 경로/Redis 인디케이터 정책. (B1, B8)
6. **25-f 외부 API 방어**: Kakao 예외 처리 + `searchAll` 격리 + URL 인코딩 + Redis timeout. (B3, B4, B5)
7. **25-g 배포 후 확인**: 스모크 테스트 절차(헬스, 가입/로그인, 검색, 저장, 캘린더), 로그에서 시크릿/토큰/SQL 노출 확인.
- 선택: 로그인 횟수 제한(B2)을 25 앞에 둘지 뒤에 둘지는 공개 범위에 따라 사용자 결정.

---

## E. 이관된 TODO 추적 (코드/파일로 확인한 것만)

| 출처 | 항목 | 상태 | 근거 |
|---|---|---|---|
| 16-1 | 사용자 조건 없는 조회 | 닫힘 | task 17, `memberId` 필수 파라미터 |
| 16-2 | isSaved가 다른 회원 포함 | 닫힘 | task 17 (`findByMemberIdAndExternalIdIn`) |
| 16-3 | null externalId 중복 체크 불일치 | **열림** | task 22 발견 2에서 재언급, 이후 처리 없음 |
| 16-4 | 테스트가 실DB에 붙음 | 닫힘 | task 23, `DemoApplicationTests`에 `@ActiveProfiles("test")` 확인 |
| 16-5 | 로컬 `jwt.*` 설정 없음 | 닫힘 | api-key 파일에 jwt 3개 키 존재(이름 확인) |
| 16-6 | StatisticsController NPE | 닫힘 | decisions 2026-11-01 |
| 16-7 | 회원 삭제 정책 | **열림** | 탈퇴 기능 없음 |
| 16-8 / 19-7 | 회원 없음 → FK 위반 500 | **열림** | task 22 발견 5에서도 열림 |
| 17 | 컨트롤러 `memberId = 1L` | 닫힘 | `grep "= 1L"` 결과 없음 |
| 17 | 하이라이트 소유권 | 닫힘 | task 18 |
| 17 | 운영 PG 네이티브 쿼리/인덱스 | 부분 | 쿼리는 Testcontainers(20), 인덱스는 로컬 PG17 EXPLAIN(22). **실제 Supabase에서 네이티브 2개 실행**은 기록 없음 |
| 19-1 | 프론트 Bearer 확인 | 닫힘 | task 21 (수동 검증 체크리스트 완료 표기) |
| 19 | actuator exposure 명시 | **열림** | backlog에만 있음(yml이 gitignore라 사용자 수정 필요) |
| 20-2 | Docker 29 호환 | **열림(로컬)** | 이번 실행에서도 skip 9 |
| 21-1 | 기존 이메일 정리 SQL + `uk_members_email` 존재 확인 | **기록 없음** | task 파일에 실행 결과 없음 |
| 22 | `perf_test` 스키마 정리(99-cleanup.sql) | **기록 없음** | 사용자 작업, 완료 기록 못 찾음 |
| 23-2,3 / 24 | 테스트 폴더 `DemoApplication`·`injectMocks.java` | **열림** | 파일 아직 존재 |
| 24 | gradlew 실행 비트 | 닫힘(기록만 낡음) | 현재 100755 |
| 24 | 홈의 `tmp_test.log` 삭제 | 닫힘으로 보임 | 홈 경로에 파일 없음(확인) |
| 24/24-1 | CI Secrets 등록, 첫 run 결과, PG 9개 skipped=0 | **기록 없음** | C 목록 |

## F. 로드맵 ↔ task/decisions 불일치
1. **CI 결과:** 로드맵은 "Testcontainers 9개 CI 통과, 223 실행/skipped 0 완료"인데 task 24, 24-1은 그 결과를 "직접 확인하지 못한 것"으로만 남겼다. 결과가 어디에도 기록되지 않았다.
2. **decisions.md 누락:** task 24의 "decisions 후보(사용자 요청 시 추가)"가 반영되지 않았다. 아래 "decisions 추가" 항목으로 보완(없는 항목만 추가).
3. **Redis 캐싱:** 로드맵은 "완료"이고 코드(`RedisCacheConfig`, `CacheErrorConfig`, `@Cacheable`)도 있으나, 이 캐싱의 task 파일이 없고 decisions 26번("Redis 캐싱/비동기는 후순위")·`CLAUDE.md`("나중에 하기로 함, 먼저 제안하지 말 것")는 옛 상태다. (비동기만 미룸으로 읽는 것이 맞는지 정리 필요)
4. **decisions 45:** "이미지는 postgres:15-alpine으로 가정"이지만 현재 테스트 코드는 `postgres:17-alpine`(task 20 후반에 변경). 로드맵의 "PostgreSQL 17 실행 검증"과는 일치, decisions는 낡음.
5. **decisions 53:** "실제 DB 제약 존재는 미확인"이지만 task 22 발견 4는 "해소(모두 존재)".
6. **decisions 8:** "Content.userId는 1L 유지"는 decisions 32(Member 연결)로 대체됐으나 대체 표시가 없다.
7. **task 18 문서:** "tasks 15~17 파일이 없다"고 적었으나 16, 17은 지금 존재(15는 없음).
8. **backlog.md:** 해결된 항목이 모두 `[ ]`로 남아 있다(예: 이메일 중복 허용, 비밀번호 72바이트, StatisticsController NPE, 실DB 테스트, `GET /api/highlights`, 컨트롤러 인증 전환). 진행 현황 판단에 쓰려면 정리 필요.
9. **Phase 12 "`docs/*.md` 검토":** `docs/`는 `origin/feat/security` 브랜치에만 있고(task 00) 현재 main에는 없다. 해당 브랜치가 원격에 남아 있다.
10. **Phase 6 "Supabase 테이블 데이터 삭제", "팀원 공유":** 코드/파일로는 검증할 수 없다(웹/사람 확인).
11. **task 21 수동 검증 체크리스트:** 16개 항목이 `[x]`지만 같은 파일 앞쪽에는 "아직 브라우저에서 실행하지 않았다"는 문구가 그대로 남아 있다(체크는 이후 사용자가 한 것으로 보임).

---

## G. 사용자가 직접 확인할 것 (내가 확인하지 못함)
**GitHub**
- Actions → 최신 main run: 초록불인지, Summary의 `ContentRepositoryPostgresTest: tests=9, skipped=0`인지, 총 223개인지(결과를 task 24-1에 기록)
- Settings → Secrets: `KAKAO_KEY`, `TMDB_TOKEN` 등록 여부(없어도 통과)
- 레포 공개/비공개 여부(A1의 옛 커밋 노출 판단에 필요), `feat/security` 등 원격 브랜치 정리 여부
- Secret scanning / push protection 알림이 있는지
-> secret scanning에 알림 없고, actions main 초록불, settings에 시크릿 등록함, 레포 공개상태.

**Supabase**
- 옛 DB 비밀번호가 변경됐는지(커밋 `473ed18`, `77528bd`, `5163c76`, `1715472`에 있던 값)
- `members`에 `uk_members_email`, `content`에 `uk_content_member_external_category`·`idx_content_member_view_date`·`member_id` FK가 있는지(`90-check-real-schema.sql`)
- `perf_test` 스키마가 지워졌는지(`99-cleanup.sql`), 이메일 중복/미정규화 데이터가 없는지
- 운영용 DB 계정/비밀번호를 로컬 개발용과 분리할지, pooler(6543) + `prepareThreshold=0` 설정은 현재 URL에 이미 포함돼 있음(코드로 확인)
-> 옛값 이미 폐기 후 재발급함

**Kakao / TMDB / Upstash**
- 옛 REST 키/토큰이 폐기(재발급)됐는지, 운영용 키를 새로 발급할지
- Upstash 비밀번호 회전, 무료 한도·연결 제한, TLS(`ssl.enabled`) 설정
-> 옛값 이미 폐기 후 재발급함

**이 PC**
- 이 세션 기록에 현재 시크릿 5개가 노출됐으므로(A1) 기록 공유 금지, 필요하면 재발급

---

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 23개 task 동안 로컬(gitignore된 설정 파일 + 실DB)에서 자라온 앱을 처음으로 설정 파일 없는 환경(Docker/클라우드)에 올리려 한다. 어디서 로컬 전제가 깨질지 코드로 확인하지 않은 상태였다.
2. **고려한 선택지**: ① 바로 Dockerfile부터 만들며 문제를 만날 때마다 수정 ② 코드를 건드리지 않고 읽기 전용으로 체크리스트를 먼저 만들기 ③ 스캐너 도구 도입.
3. **선택 이유**: ②. 비용 없이 "배포 전 필수"와 "나중에"를 분리해 task 25를 작게 쪼갤 수 있다. 한계: 실행하지 못한 항목(GitHub/Supabase 웹, 앱 기동)은 추정으로 남고 "확인하지 못한 것"에 분리했다.
4. **막혔던 부분과 해결**: 시크릿 값을 가린 채 설정 파일 구조만 보려던 셸 마스킹이 실패해 값이 도구 출력에 노출됐다. 이후 git 이력 검사는 값을 변수로 읽어 `-S` 검색의 **건수만** 출력하는 방식으로 바꿨다. 교훈: 시크릿이 든 파일은 "마스킹해서 읽기"보다 `grep -o '^[a-z-]*:'`처럼 키 이름만 뽑는 방식이 안전하다.

## 복습 질문
(토글 안에 답을 적어주세요. 프론트 질문은 없다.)

<details><summary>Q1. 운영 DB에서 <code>ddl-auto: update</code>가 위험한 이유는 무엇인가요? 이 프로젝트에서 실제로 문제가 될 뻔한 사례(task 16, 21-1)를 하나 들어 설명해보세요.</summary>

(여기에 답)

</details>

<details><summary>Q2. 시크릿을 환경변수로 주입할 때 Spring이 어떻게 프로퍼티에 바인딩하나요? <code>api.kakao-key</code>(<code>@ConfigurationProperties</code>)와 <code>app.cors.allowed-origins</code>(<code>@Value</code>)는 환경변수 이름을 어떻게 써야 하고, 둘의 바인딩 방식은 어떻게 다른가요? (예: <code>API_KAKAOKEY</code>)</summary>

(여기에 답)

</details>

<details><summary>Q3. (기존 코드 연결) <code>runtimeOnly h2</code>가 운영 클래스패스에 있고 <code>SPRING_DATASOURCE_URL</code>이 빠진 채 기동하면 무슨 일이 생길 수 있나요? 반면 <code>JwtProperties</code>는 왜 같은 실수에서 기동이 실패하고, <code>ApiConfig</code>는 왜 실패하지 않나요?</summary>

(여기에 답)

</details>
