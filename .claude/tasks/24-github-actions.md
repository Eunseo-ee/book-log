# 24. GitHub Actions CI

- 번호: 기존 최대 번호 23(`23-test-environment.md`) 다음이라 24 맞음. 브랜치 `feat/auth-member`, 커밋하지 않음.
- 목표: push / pull_request 시 `./gradlew test` 자동 실행, 시크릿은 GitHub Secrets로 주입.
- [x] push/PR 시 `./gradlew test` (워크플로 작성. 실제 실행은 미확인)
- [x] 시크릿은 GitHub Secrets로 주입 (구조만 마련, 값 없어도 통과하도록)

## 시작 전 확인 (코드로 확인한 사실)
- 레포 기본 브랜치: `main` (`origin/HEAD -> origin/main`). 작업 브랜치는 `feat/*`.
- `.github/`에는 이슈/PR 템플릿만 있었고 workflows는 없었다.
- Gradle 프로젝트 루트는 `demo/` (gradlew, build.gradle). git에는 `demo/gradlew`가 모드 100644(실행 권한 없음)로 저장되어 있어 CI에서 `chmod +x`가 필요하다.
- **task 23에서 이미 DB 테스트가 H2로 전환됨**: `DemoApplicationTests`, `HighlightRepositoryTest`는 `@ActiveProfiles("test")` + `src/test/resources/application-test.yml`(H2, replace none, 가짜 jwt)로 돈다. 즉 지시문의 전제("HighlightRepositoryTest가 설정된 실제 DB에 붙는다")는 지금 코드에는 해당하지 않는다.
- `ContentRepositoryPostgresTest`는 Testcontainers(`postgres:17-alpine`, `@ServiceConnection`)로 자체 컨테이너를 띄우고, Docker가 없으면 skip한다.
- `ApiConfig`(prefix `api`): `kakaoKey`, `tmdbToken`. 필드 검증이 없어 null이어도 빈 생성 가능(task 23에서 확인). `@Value`는 `app.cors.allowed-origins`(기본값 있음)뿐.

## 설계 선택과 이유
### 1) DB: Postgres 서비스 컨테이너를 쓰지 않는다 (지시문과 다른 점)
- 지시문의 목적은 "CI가 운영 DB(Supabase)를 건드리지 않게" 하는 것인데, task 23의 H2 test 프로파일로 이미 달성되어 있다. 서비스 컨테이너를 추가해도 접속하는 테스트가 없어 죽은 설정이 된다.
- PostgreSQL 엔진 검증이 필요한 `ContentRepositoryPostgresTest`는 Testcontainers가 ubuntu runner의 Docker로 직접 컨테이너를 띄운다. 서비스 컨테이너와 포트·URL을 이중으로 맞출 필요가 없다. 부수 효과: 로컬에서 Docker가 꺼져 skip되던 9개 테스트가 **CI에서는 실제로 실행될 것**이다(미확인, 아래 참고).
- 대안: 서비스 컨테이너 + `SPRING_DATASOURCE_*` 환경변수(또는 `application-ci.yml`)로 테스트 전체를 진짜 PostgreSQL에 붙이는 방식. 장점은 H2 비호환 문제가 없다는 것, 단점은 `@ActiveProfiles("test")`가 테스트 클래스에 박혀 있어 test 프로파일이 환경변수보다 약하게 적용될 수 있고(프로파일 파일과 env 우선순위가 섞임), 기존 테스트 수정 없이는 일관되게 붙이기 어렵다. 필요해지면 별도 task로 한다.
- `application-ci.yml` 신규 커밋과 환경변수만 오버라이드 두 후보 모두 **채택하지 않음**: 이미 `application-test.yml`이 같은 역할을 하고 로컬 설정을 가리지 않는다.
- DB 접속 정보가 시크릿이 아닌 이유: DB가 CI 안에서 뜨는 임시 H2/컨테이너라 값이 외부에 아무 의미가 없다(Testcontainers가 무작위 포트·기본 계정을 만든다). 운영 DB 접속 정보는 아예 CI에 주입하지 않는다.

### 2) 시크릿
- `API_KAKAOKEY` ← `secrets.KAKAO_KEY`, `API_TMDBTOKEN` ← `secrets.TMDB_TOKEN`.
- Spring의 환경변수 relaxed binding은 정규형 이름(`api.kakao-key`)에서 점→밑줄, 대시 제거, 대문자 변환을 한 형태를 쓰므로 `API_KAKAOKEY`, `API_TMDBTOKEN`이 맞다(`API_KAKAO_KEY`로 쓰면 `api.kakao.key`로 읽혀 바인딩되지 않는다).
- 현재 테스트는 외부 API를 모킹하고 task 23에서 키 없이 컨텍스트가 뜨는 것을 확인했으므로 **값이 비어도 통과하는 구조**다. 시크릿 미등록이면 빈 문자열이 바인딩된다. 포크 PR에서도 시크릿이 비어 있을 뿐 테스트는 돈다.
- 시크릿은 `Test` 스텝의 `env`에만 둔다(다른 스텝에 노출 안 함). 워크플로에서 값을 echo/파일로 쓰지 않는다. GitHub가 로그에서 시크릿 값을 마스킹하지만 의존하지 않는다.
- JWT secret은 주입하지 않는다: `application-test.yml`의 가짜 값을 쓴다.

### 3) 워크플로 `.github/workflows/ci.yml`
- 트리거: `push`(모든 브랜치) + `pull_request`(대상 `main`). PR 없이 push로 merge하는 경우가 많고 작업 브랜치(`feat/*`)에서도 돌려 보고 싶어서 push는 브랜치 제한을 두지 않았다. 같은 브랜치 push+PR 중복 실행은 `concurrency`로 이전 실행을 취소한다(트레이드오프: 브랜치 push 건수가 많으면 Actions 분 소모. 줄이려면 push를 `main`만으로 제한).
- `permissions: contents: read`(최소 권한), `timeout-minutes: 20`.
- `defaults.run.working-directory: demo`, JDK 21 temurin, `gradle/actions/setup-gradle@v4`(캐시), `chmod +x gradlew`.
- `./gradlew test --no-daemon`. 실패 시(`if: failure()`) `demo/build/reports/tests`, `demo/build/test-results`를 artifact(7일)로 업로드.

## 변경/추가한 파일
| 파일 | 변경 |
|---|---|
| `.github/workflows/ci.yml` | 신규 |
| `.claude/tasks/24-github-actions.md` | 신규 (이 파일) |
기존 소스·테스트·application.yml·.gitignore·decisions.md는 수정하지 않았다. (decisions.md 항목 추가는 사용자 요청 시. 아래 "decisions 후보" 참고)

## 검증 (실제 결과)
- 로컬 `./gradlew test --rerun-tasks`(demo/): **BUILD SUCCESSFUL, 223개 실행, 실패 0, 에러 0, skip 9** (build/test-results XML 집계). skip 9개는 전부 `ContentRepositoryPostgresTest`로, 이 PC에서 Docker 데몬이 응답하지 않아 skip된 것(task 23과 동일). main `application.yml`이 있는 로컬 환경에서의 결과다. 이번에 yml을 치우고 다시 돌리지는 않았다(task 23에서 이미 확인).
- 워크플로 YAML: Python `yaml.safe_load`로 구문 파싱 성공(on/jobs/steps 구조 확인). actionlint는 설치되어 있지 않아 **스키마·액션 입력 검증은 육안 검토만** 했다.

## 직접 실행해 확인하지 못한 것
1. GitHub Actions에서의 실제 실행 결과 전부 (push 후 Actions 탭에서 확인 필요).
2. CI(ubuntu runner)에서 `ContentRepositoryPostgresTest` 9개가 실제로 실행되고 통과하는지. 이 PC에서는 skip이라 한 번도 실행된 적이 없다. 특히 PostgreSQL 전용 네이티브 쿼리(`EXTRACT(DOW)` 등)가 처음으로 실제 엔진에서 돌므로, 여기서 실패가 나면 코드 문제일 수 있다(이번 task에서 고치지 않음).
3. 환경변수 `API_KAKAOKEY`/`API_TMDBTOKEN` 바인딩을 실제로 실행해 보지는 않았다(규칙 기반 설명). 값이 없어도 통과한다는 것은 task 23 결과에 근거.
4. `gradle/actions/setup-gradle@v4`, `actions/*@v4` 버전은 알고 있는 최신 메이저를 쓴 것으로, 이번에 레지스트리에서 확인하지 않았다.
5. CI 환경(main yml 없음)에서의 전체 `./gradlew test` — 이 PC에서 재현(yml 이름 변경)은 task 23에서만 했고 이번에는 하지 않았다.

## 발견한 문제 (고치지 않음)
1. `src/test/java/book_log/demo/DemoApplication.java`가 main과 같은 FQCN (task 23 발견 2와 동일).
2. `src/test/java/.../service/injectMocks.java`는 빈 `@interface` (task 23 발견 3).
3. `RedisCacheConfig`가 `RedisCacheManager`를 직접 만들어 `spring.cache.type: simple`이 무시됨. 캐시를 실제로 타는 `@SpringBootTest`가 생기면 CI에 Redis가 없어 실패할 수 있다 (task 23 발견 4).
4. `demo/gradlew`가 git에서 100644. `git update-index --chmod=+x demo/gradlew`로 고치면 워크플로의 chmod 스텝이 필요 없어지지만, 이번 범위 밖이라 워크플로에서 우회했다.
5. 실행 중 사고: 로그를 `../../../../../../tmp_test.log`로 리다이렉트해 `C:\Users\tooth47348\tmp_test.log`가 홈에 생겼다(프로젝트 밖). 삭제는 안전장치로 막혀 수행하지 않았으니 직접 지워도 된다.
6. 워크플로가 `push` 전 브랜치에서 돌므로 Actions 사용량이 늘 수 있다(설계 3 참고).

## 사용자가 해야 할 일
1. GitHub 레포 → Settings → Secrets and variables → Actions → New repository secret:
   - `KAKAO_KEY` (Kakao REST API 키)
   - `TMDB_TOKEN` (TMDB 토큰)
   등록하지 않아도 CI는 통과한다(현재 테스트는 외부 API 모킹). 실제 API를 부르는 테스트를 만들 때 필요하다.
2. 변경을 커밋·push한 뒤 Actions 탭에서 첫 실행 결과 확인. 특히 `ContentRepositoryPostgresTest` skip 수(0이어야 정상)와 실패 시 `test-reports` artifact.
3. 필요하면 위 4번(gradlew 실행 비트)과 push 트리거 범위를 정한다.

## decisions 후보 (사용자 요청 시 추가)
- 2026-11-03: CI는 서비스 컨테이너 없이 H2 test 프로파일 + Testcontainers(runner의 Docker)로 돌린다. 운영 DB 접속 정보는 CI에 주입하지 않고, API 키는 `KAKAO_KEY`/`TMDB_TOKEN` 시크릿을 `API_KAKAOKEY`/`API_TMDBTOKEN` 환경변수로 주입하되 없어도 통과한다 (task 24)

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 테스트가 로컬에서만 검증되고 push마다 자동 검증이 없었다. 설정 파일(application.yml)이 gitignore 대상이라 CI에는 없고, 운영 DB·API 키가 CI에 새면 안 된다.
2. **고려한 선택지**: ① Postgres 서비스 컨테이너 + 환경변수/`application-ci.yml` 주입 ② task 23의 H2 test 프로파일 그대로 + Testcontainers는 runner의 Docker 사용 ③ 시크릿으로 application.yml 통째 복원.
3. **선택 이유**: ②. DB 접속이 이미 CI 안에서 닫혀 있어 서비스 컨테이너가 중복이고, 설정 파일을 복원하는 ③은 운영 DB 접속 정보가 CI에 들어가 목표에 어긋난다. 트레이드오프: 일반 테스트는 H2라 PostgreSQL 전용 동작은 Testcontainers 테스트에 의존한다.
4. **막혔던 부분과 해결**: 지시문의 전제(실DB에 붙는 `@DataJpaTest`)가 task 23 이후 사실과 달랐다 → 코드를 확인해 서비스 컨테이너를 쓰지 않기로 하고 이유를 기록. gradlew 실행 권한(100644)은 워크플로 `chmod +x`로 우회. 환경변수 이름은 relaxed binding 규칙(대시 제거)에 맞춰 `API_KAKAOKEY`로 했다.

## 복습 질문
(토글 안에 답을 적어주세요. 프론트 질문은 없다.)

<details><summary>Q1. CI에서 테스트를 Supabase 대신 H2/컨테이너로 돌리는 이유는 무엇이고, 이 프로젝트에서 서비스 컨테이너를 따로 두지 않은 이유는 무엇인가요?</summary>

(여기에 답)

</details>

<details><summary>Q2. GitHub Secrets를 `API_KAKAOKEY` 환경변수로 넣으면 Spring이 어떻게 `ApiConfig.kakaoKey`에 바인딩하나요? `API_KAKAO_KEY`로 쓰면 왜 안 되나요?</summary>

(여기에 답)

</details>

<details><summary>Q3. (기존 코드 연결) task 23 이전의 `HighlightRepositoryTest`는 `@AutoConfigureTestDatabase(replace = NONE)`였습니다. 이 설정이 CI에서 왜 위험했고, 지금은 어떤 설정이 그 위험을 막고 있나요?</summary>

(여기에 답)

</details>
