# 25-b. Dockerfile + .dockerignore

- 번호: 25-a와 같은 `N-하위문자-이름` 규칙. 커밋하지 않음.
- 목표: 비밀값과 application*.yml이 없는 이미지를 만들고, 설정은 전부 환경변수로 받아 컨테이너가 뜨는 것까지. 플랫폼 전용 설정 없음.

## 코드 현황 확인 (요청서와 다른 점)
- 빌드 파일이 `demo/`에 있어 **Dockerfile과 .dockerignore는 `demo/`에 두고 빌드 컨텍스트도 `demo/`**로 했다 (`docker build -t book-log ./demo`). 루트의 `.claude/`, `frontend/`, `*.md`는 컨텍스트 밖이라 애초에 전송되지 않는다.
- `demo/bin/`(Eclipse 출력, gitignore 대상)이 로컬에 있어 .dockerignore에 추가했다.
- 환경변수는 25-a 표를 그대로 사용(`.env.example`과 동일 이름).

## 변경한 파일
| 파일 | 내용 |
|---|---|
| `demo/Dockerfile` | 신규. 멀티스테이지 |
| `demo/.dockerignore` | 신규 |
| `.claude/tasks/25-b-dockerfile.md`, `.claude/decisions.md` | 신규 / 항목 추가 |

25-a 코드/설정, application*.yml, HighlightRepositoryTest는 건드리지 않았다.

## 설계
- 빌드: `eclipse-temurin:21.0.13_9-jdk-noble`. `gradlew/settings.gradle/build.gradle/gradle/`만 먼저 COPY해 `dependencies`로 의존성을 캐시 레이어에 받고, 그 뒤 `src`를 COPY해 `bootJar -x test`.
- 런타임: `eclipse-temurin:21.0.13_9-jre-noble` + jar만. 베이스에 이미 있는 `ubuntu`(uid 1000) 계정으로 실행(`USER ubuntu`).
- `ENV TZ=Asia/Seoul`, `EXPOSE 8080`, ENTRYPOINT `sh -c "exec java -XX:MaxRAMPercentage=75 -Dserver.port=${PORT:-8080} -jar app.jar"`.
- 베이스 태그 이유: 구체 버전(21.0.13_9)으로 고정해 빌드마다 JDK가 바뀌지 않게 했고(`21`/`latest` 금지), 확인 시점에 Docker Hub에서 조회된 21.0.x 중 가장 높은 패치다. noble(Ubuntu 24.04 LTS)을 골랐다. 다이제스트 고정은 하지 않음(발견 8번).
- .dockerignore: `.env*`, `src/main/resources/application*.yml|yaml|properties`, `.gradle`, `build`, `bin`, IDE, `.git`, `.claude`, `*.md` 등.
  - **패턴을 `src/main/resources/`로 한정**했다. `application*.yml` 전체로 막으면 `src/test/resources/application-test.yml`까지 빠진다. 이번 빌드는 `bootJar`가 main 클래스/리소스만 쓰고 test 소스를 컴파일하지 않으므로 어느 쪽이든 빌드는 되지만, 나중에 이미지 안에서 테스트를 돌릴 때 깨지지 않게 한정했다(`src`는 통째로 COPY되어 test 소스도 빌드 스테이지에는 들어가지만 jar에는 안 들어간다).

## 검증 결과 (Docker 데몬 응답함: Docker Desktop 29.7.2)
| 항목 | 결과 |
|---|---|
| `docker build` | 성공 |
| jar 안 설정 파일 | `BOOT-INF/classes` 아래 `.yml/.yaml/.properties` **0개** (Python zipfile로 확인). h2 jar도 없음 |
| 이미지 크기 | 544MB (`docker images`) |
| 실행 사용자 | `uid=1000(ubuntu)`, PID 1 프로세스가 `java` (exec 적용 확인) |
| 키 없이 실행 (DB env는 있고 API 키만 없음) | 기동 실패, `api.tmdbToken`/`api.kakaoKey` "must not be blank" 출력 |
| 환경변수 전혀 없이 실행 | 기동 실패, 단 **DataSource url 에러가 먼저** 나와 키 이름은 안 보임(발견 2번) |
| 더미 키 + 일회용 postgres:15-alpine + redis:7-alpine (사용자 정의 네트워크) | 기동 성공, `/actuator/health` → 200 `{"status":"UP"}`. 이 스모크에서만 `DDL_AUTO=update` 사용 |
| 타임존 | 컨테이너 `date` = KST, `TZ=Asia/Seoul`, Spring 로그 타임스탬프 `+09:00`. `-XshowSettings`에는 `user.timezone`이 안 나와(TZ 환경변수에서 유도되는 값) JVM 설정값을 직접 출력해 보지는 못했다. 로그의 +09:00가 근거 |
| `PORT=9090` | `Tomcat started on port 9090`, 호스트 19090으로 `/actuator/health` 200. PORT 없으면 8080 |
| 정리 | 컨테이너 4개, 네트워크, 내 이미지, postgres/redis 이미지 삭제 확인. 베이스 `eclipse-temurin:21.0.13_9-jre-noble`은 캐시로 남아 있음 |

실제 Supabase/Upstash 주소·계정·키는 쓰지 않았다. 더미 값과 임시 env 파일(삭제함)만 사용했다.
**미검증**: ddl-auto=validate로 실제 스키마 기동, Redis 캐시 읽기/쓰기(스모크는 /actuator/health만 호출), 실제 Supabase 연결, 로그인 등 API 동작.

## ./gradlew test (Docker 응답하는 상태)
- BUILD SUCCESSFUL, **230개, 실패 0, 에러 0, skipped 9**. 25-a와 같다. (처음 실행은 UP-TO-DATE라 이전 결과를 읽은 것이어서 `cleanTest`로 재실행해 확인)
- **ContentRepositoryPostgresTest 9개는 여전히 skip, 통과가 확인되지 않았다.** 원인: Docker는 응답하는데 Testcontainers 1.20.4(Boot 3.4.1 BOM)가 `NpipeSocketClientProviderStrategy: BadRequestException (Status 400)`로 Docker 환경을 인식하지 못하고, `disabledWithoutDocker=true`라 조용히 skip된다(`--info` 로그로 확인). 추정(미확인): Docker Engine 29의 최소 API 버전 상향과 구버전 docker-java(3.4.0)의 불일치. `DOCKER_API_VERSION=1.44` 환경변수를 주고 한 번 돌려 봤지만 skip은 그대로였다(해결 못 함). 수정(Testcontainers 업그레이드 등)은 범위 밖이라 하지 않았다.

## yml이 사라질 때 달라지는 설정 (기록만, 고치지 않음)
| yml 키 | yml 값 의도 | 이미지(yml 없음)의 기본 동작 | 달라지나 |
|---|---|---|---|
| `spring.application.name` | demo | 미설정 | 기능 영향 없음. 로그의 `[demo]` 표기가 사라짐(컨테이너 로그에서 확인) |
| `spring.profiles.include: api-key` | 키 파일 포함 | 프로파일 없음("default"만, 로그로 확인) | 영향 없음. 값은 환경변수로 대체 |
| `spring.data.redis.ssl.enabled: true` | Upstash TLS | **기본 false** | **달라짐(중요)**: TLS가 필요한 Redis면 `SPRING_DATA_REDIS_SSL_ENABLED=true`를 꼭 줘야 한다. 25-a 표에는 "선택(기본 false)"로만 적혀 있어 누락되기 쉽다 |
| `spring.cache.type: redis` | 캐시 종류 | 미설정 → 자동 감지 | 영향 없음(코드 근거): `RedisCacheConfig`가 `RedisCacheManager` 빈을 직접 만들어 Boot 자동 구성은 물러난다. 런타임으로 캐시 동작은 미확인 |
| `spring.datasource.driver-class-name` | PG 드라이버 | JDBC URL에서 추론 | 영향 없음. 스모크가 이 키 없이 기동됨 |
| `spring.datasource.url`의 `?prepareThreshold=0` | Supabase pooler(6543) 대응 | URL 전체가 환경변수 | 달라질 수 있음: `SPRING_DATASOURCE_URL`에 이 쿼리 파라미터를 빠뜨리면 pooler에서 prepared statement 오류 가능(미검증). `.env.example`에 설명 없음 |
| `spring.jpa.hibernate.ddl-auto: update` | 자동 생성 | env로 `validate` 지정 시 검증만. 미지정 시 Boot 기본은 임베디드 DB가 아니면 `none` | 달라짐(의도된 변경). 스모크는 빈 DB라 update 사용 |
| `spring.jpa.show-sql: true` | SQL 출력 | 기본 false | 달라짐(의도됨) |
| `hibernate.format_sql: true` | 포맷 | 기본 false | show-sql=false라 영향 없음 |

## 확정된 결정
1. Dockerfile/.dockerignore는 `demo/`에 두고 컨텍스트도 `demo/`.
2. 멀티스테이지, JDK는 빌드 스테이지에만. 런타임은 JRE + jar만.
3. 설정 파일 제외는 `src/main/resources/application*`로 한정. 비밀은 ENV로 굽지 않아 이미지 레이어에 남지 않는다.
4. 포트는 ENTRYPOINT의 `${PORT:-8080}`, `exec`로 java가 PID 1.
5. 베이스 이미지는 `21.0.13_9-{jdk,jre}-noble` 고정.

## 발견한 문제 (고치지 않음)
1. **Testcontainers가 Docker 29를 인식 못 해** PostgreSQL 쿼리 테스트 9개가 이 PC에서 계속 skip(위 참고). CI(Linux runner)는 별개일 수 있어 24-1의 skipped 수 확인 필요.
2. 환경변수가 전혀 없을 때 첫 에러가 DataSource라 API 키 누락 메시지가 보이지 않는다. 키만 빠지면 25-a 동작대로 이름이 나온다.
3. 로그에 `Using generated security password: ...`가 찍힌다(UserDetailsService 자동 구성). 임의 값이고 사용되지 않지만, 로그 수집 시 비밀번호 형태 문자열이 남는다.
4. `-Dserver.port`가 환경변수보다 우선하므로 `SERVER_PORT`는 무시되고 `PORT`만 먹는다.
5. 이미지 544MB. 슬림화(jlink, layered jar 등)는 하지 않았다.
6. ddl-auto=update 최초 실행 시 "constraint ... does not exist, skipping" WARN이 나온다. 빈 DB에서 제약 drop 시도가 건너뛰어진 것으로 보이며 정상 동작으로 판단(미확인).
7. 25-a에서 이어진 것: validate vs 기존 운영 스키마 대조, `ssl.enabled`/`prepareThreshold` 안내 부족(`.env.example`에 주석 필요).
8. 베이스 이미지 다이제스트 고정 안 함, HEALTHCHECK 없음(25-e), 베이스 이미지 취약점 스캔은 하지 않았다.

## 25-c 이후에 넘길 사항
- 25-c(타임존 코드): 컨테이너는 TZ만 설정되어 있고 코드의 `LocalDate.now()` 등은 JVM 기본 타임존에 의존. 컨테이너 KST 확인은 끝났다.
- 25-d(CORS): `APP_CORS_ALLOWED_ORIGINS`를 실제 프론트 origin으로.
- 25-e: HEALTHCHECK, actuator 노출, Swagger 운영 비공개 여부.
- 제안(범위 밖): 로컬 재현용 `docker-compose.yml`(app + postgres + redis, `env_file: .env`). `.env.example`이 루트에 있으므로 compose를 루트에 둘지 `demo/`에 둘지 그때 결정.
- `.env.example`에 `SPRING_DATA_REDIS_SSL_ENABLED=true`(Upstash)와 `?prepareThreshold=0` 주의 주석 추가 검토.

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 이미지에 비밀과 로컬 yml이 들어가기 쉽고(bootJar가 resources를 통째로 포함), 포트·타임존도 yml/호스트에 의존했다.
2. **고려한 선택지**: ① 멀티스테이지 + .dockerignore로 yml 제외 + 환경변수 ② 운영용 yml을 이미지에 포함(프로파일) ③ 단일 스테이지에 호스트에서 빌드한 jar만 복사.
3. **선택 이유**: ①. 이미지에 비밀이 레이어로 남지 않고 JDK/Gradle 캐시가 최종 이미지에서 빠진다. 트레이드오프: 환경변수로 못 쓰는 키(format_sql)와 yml의 값(ssl.enabled 등)을 환경변수로 옮겨야 해서 배포 시 빠뜨리기 쉽다.
4. **막혔던 부분과 해결**: ① ignore 패턴을 넓게 쓰면 test yml까지 빠지는 문제 → `src/main/resources`로 한정. ② 키 없이 실행 검증 때 DataSource 에러가 먼저 나와 키 에러가 안 보임 → DB env만 주고 키를 뺐다. ③ `./gradlew test`가 UP-TO-DATE로 이전 결과를 보여줘 `cleanTest`로 재실행했고, 그래도 skip이라 Testcontainers 로그로 원인(400 BadRequest)을 확인했다(해결은 못 함).

## 복습 질문
<details><summary>Q1. 멀티스테이지 빌드를 쓰는 이유와, 이 프로젝트에서 최종 이미지에 빠지는 것은 무엇인가?</summary>

(여기에 답)

</details>

<details><summary>Q2. application*.yml을 .dockerignore로 막은 이유와, 막았을 때 생길 수 있는 부작용은?</summary>

(여기에 답)

</details>

<details><summary>Q3. 포트를 yml이 아니라 ENTRYPOINT에서 받은 이유는? (25-a의 환경변수 방식과 연결해서 설명)</summary>

(여기에 답)

</details>
