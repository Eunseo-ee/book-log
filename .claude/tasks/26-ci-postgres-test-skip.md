# 26. CI에서 ContentRepositoryPostgresTest 9개가 전부 skipped인 원인 조사

- 번호: 직전이 `25-j`라 `26`으로 이었다. 커밋하지 않음.
- 성격: **읽기 전용 조사**. 코드/워크플로/설정/decisions.md 수정 없음.
- 한계: 이 환경에는 `gh`가 없고 CI 로그에 접근할 수 없다. **CI runner의 실제 Docker 버전과 Testcontainers의 실제 skip 사유 로그는 이번에 확인하지 못했다.** CI 쪽 원인은 추정이다.

## 1. 결론 요약

1. skip 조건은 `@Testcontainers(disabledWithoutDocker = true)` 하나다. "Docker를 못 쓰면" 클래스 전체 skip. (`ContentRepositoryPostgresTest.java:32`)
2. CI(ci.yml)에는 Docker를 막는 설정이 없고 `ubuntu-latest`에서 Testcontainers에 맡긴다(`ci.yml:15, 40-41`). 워크플로 자체에는 skip을 일으킬 요소가 없다.
3. 이 테스트가 "Docker 없음"이 아니라 **"Testcontainers가 Docker를 인식하지 못함"일 때도 똑같이 조용히 skip**된다는 점이 핵심이다. 이 저장소는 이미 같은 현상을 겪었다(task 20, 25-b: Testcontainers 1.20.4 + Docker 29 → HTTP 400 → skip).
4. CI에서의 정확한 원인은 로그가 없어 **미확인**. 가장 유력한 후보는 25-b와 같은 "runner의 Docker Engine 버전 ↔ Testcontainers 1.20.4(docker-java 3.4.0) API 버전 불일치"(추정). CI에는 로컬에서 쓰던 우회(`api.version=1.44`)가 없다.
5. 초록 체크인 이유: JUnit에서 skip은 실패가 아니고, Gradle은 skip이 있어도 BUILD SUCCESSFUL이며, CI의 어떤 단계도 skipped 수를 검사하지 않는다(요약 스텝은 표시만, 실패시키지 않음 `ci.yml:44-72`).

## 2. 조사 결과 (근거)

### 2-1. skip 조건 (질문 1)
- `ContentRepositoryPostgresTest.java:32` `@Testcontainers(disabledWithoutDocker = true)`. 다른 skip 수단(`@Disabled`, `@EnabledIf*`, `Assumptions`)은 이 클래스에 없다(demo/src/test grep: `@Disabled|EnabledIf|assumeTrue` 매치 없음, `Testcontainers` 매치는 이 파일과 `SchemaSqlSyncTest`의 주석뿐).
- 동작: JUnit 확장이 클래스 실행 전에 `DockerClientFactory.isDockerAvailable()`을 호출하고 false면 클래스 전체를 "disabled"(=skipped)로 처리한다. 이 함수는 Docker 데몬이 없을 때뿐 아니라 **연결/버전 협상이 실패해도 false**를 돌려준다(라이브러리 동작에 대한 내 설명이며, 이번에 소스를 열어 확인하지는 않았다. 근거는 25-b의 `--info` 로그: `NpipeSocketClientProviderStrategy: BadRequestException (Status 400)` 후에도 skip).
- 컨테이너: `postgres:17-alpine`(`:47`). `@ServiceConnection`으로 데이터소스 주입.
- 의존성/버전: `build.gradle:57-59`, Boot 3.4.1 BOM → Testcontainers 1.20.4 (task 20, 25-b 기록).

### 2-2. CI 워크플로 (질문 2)
- `.github/workflows/ci.yml`: `runs-on: ubuntu-latest`(15행), 테스트 명령 `./gradlew test --no-daemon`(41행), working-directory `demo`.
- Docker 설정/서비스 컨테이너/프로파일 환경변수 없음. env는 `API_KAKAOKEY`, `API_TMDBTOKEN`뿐. 주석(36-37행)이 "Testcontainers가 runner의 Docker로 직접 띄운다"고 가정하고 있다.
- 사용 Docker 버전을 고정하거나 확인하는 스텝 없음. `ubuntu-latest`의 이미지 버전은 시점에 따라 바뀐다(런너 이미지 정보는 이번에 조회하지 못함 → 미확인).
- Job Summary 스텝(`ci.yml:44-72`)은 skipped==tests이면 "(Docker 없이 건너뜀)"이라는 **문구만** 붙인다. 문구는 사실을 단정하지만 실제 원인은 "Docker 없음"이 아닐 수 있다(발견 1).

### 2-3. 로컬 (질문 3)
- 이번에 실행: `cd demo && ./gradlew test --tests '*ContentRepositoryPostgresTest' --no-daemon` → BUILD SUCCESSFUL. 결과 XML `TEST-...ContentRepositoryPostgresTest.xml`: `tests="9" skipped="9" failures="0" errors="0"` (확인함).
- 이 시점 이 PC의 Docker: `docker version`에서 Client 29.7.2만 나오고 `docker ps`가 `dockerDesktopLinuxEngine` 파이프를 못 찾음 → **데몬이 꺼져 있어** 로컬 실행 가능 여부는 지금 확인 못 했다. 즉 이번 로컬 skip은 "Docker 미실행" 때문이다.
- 과거 기록(이번에 재실행한 것 아님): task 20에서는 `-Dapi.version=1.44`를 줘서 9개 통과, 24-1에서는 데몬 무응답으로 skip 9, 25-b에서는 Docker 29 + Testcontainers 1.20.4 비호환으로 `api.version=1.44` 환경변수로도 해결 못 하고 skip. 현재 `~/.testcontainers.properties`에 `api.version=1.44`, `docker.client.strategy=NpipeSocketClientProviderStrategy`가 있다(내용은 저장소 밖 사용자 홈 파일, 시크릿 아님).
- 정리: **로컬에서도 항상 실행되는 것이 아니다.** 데몬이 켜져 있고 API 버전 협상이 되는 때만 실행된다.

### 2-4. CI에서만 skip되는가 → 원인 판별 (질문 3 후반)
| 후보 | 근거 | 판정 |
|---|---|---|
| (A) runner에 Docker 없음 | ubuntu-latest는 Docker 설치돼 있음(24-1에서도 "설치돼 있어 실행 예상"으로 적음, 직접 확인한 건 아님) | 가능성 낮음(미확인) |
| (B) Docker는 있는데 Testcontainers 1.20.4가 인식 실패(API 버전 불일치) | 같은 증상을 로컬에서 재현·기록(task 20, 25-b). CI에는 우회 설정 없음 | **가장 유력(추정)** |
| (C) 워크플로/어노테이션 문제 | ci.yml에 Docker 차단 요소 없음, 어노테이션은 위와 같음 | 아님 |
| 확정 방법 | Actions 로그 또는 artifact `test-reports`의 HTML 리포트/`--info` 로그에서 `Could not find a valid Docker environment` 또는 strategy별 예외를 확인 | **이번에 못 함** |

### 2-5. 같은 skip 조건을 쓰는 다른 테스트 (질문 4)
- `@Testcontainers`/`disabledWithoutDocker`를 쓰는 테스트는 **이 클래스 하나뿐**이다. `SchemaSqlSyncTest`는 주석(`:25`)에 "Testcontainers는 쓰지 않는다"고만 나오고 H2(test 프로파일)를 쓴다.
- 클래스 단위 skip이라 영향 범위는 정확히 9개 = 전체 skipped 9와 일치(CI 요약: 334 중 skipped 9).
- 이 skip으로 **PostgreSQL 전용 쿼리(`findActiveDays`, `findMostActiveDay` 등 EXTRACT/LIMIT)는 CI에서 검증되지 않는다**. decisions.md 2026-11-03에 "H2로는 미검증"이라고 명시된 바로 그 영역이다.

## 3. 해결 방안 후보

| | (1) Testcontainers를 CI에서 정상 동작시키기 | (2) CI에 Postgres service container 추가 | (3) skip 조건 변경(CI에서는 skip이면 실패) |
|---|---|---|---|
| 방법 | 먼저 로그로 원인 확정. (B)면 Testcontainers 업그레이드(Boot BOM 오버라이드, 1.21+/2.x) 또는 `api.version`을 런타임에 주입(`DOCKER_API_VERSION`/`~/.docker-java.properties`/`systemProperty`) | ci.yml에 `services: postgres:17`, 테스트는 `@ServiceConnection` 대신 접속 정보를 환경변수/프로퍼티로 받도록 변경 | `disabledWithoutDocker`를 빼거나, CI에서만 `CI=true`일 때 skip이면 실패하게 함. 또는 Summary 스텝에서 skipped>0이면 exit 1 |
| 장점 | 로컬과 CI가 같은 방식. 테스트 코드 유지. 근본 해결 | Docker-in-Docker 이슈와 무관, runner에서 안정적이고 빠름 | 구현이 가장 작음. "초록인데 안 돌았다"를 즉시 드러냄 |
| 단점 | BOM 오버라이드는 build.gradle 변경 + 다른 Testcontainers 호환 확인 필요. 원인이 (B)가 아니면 헛수고 | 테스트가 Testcontainers 방식과 갈라져 로컬/CI 경로가 둘이 됨. DB 이름/스키마 정리 책임이 생김. 코드·워크플로 모두 수정 | 원인을 고치는 게 아니라 알려 줄 뿐. (3)만 하면 CI가 **빨갛게** 바뀜 |
| 위험 | 버전 올리면 다른 테스트 영향 가능(낮음) | 테스트 격리(ddl-auto=create)가 공유 DB에서 어떻게 동작하는지 재확인 필요 | 로컬에서 Docker 없는 팀원은 깨짐(옵션을 CI 한정으로 두면 회피) |

### 추천안: **(1) → (3) 순서로 둘 다, 단 (1)의 첫 단계는 원인 확정**
1. 먼저 CI 로그/artifact에서 skip 사유를 확정한다(코드 변경 없음).
2. 사유가 (B)이면 Testcontainers를 올려 CI에서 실제로 돌게 한다(1).
3. 이후 "Postgres 테스트 skipped>0이면 CI 실패"를 추가한다(3). 이유: 지금 문제의 본질은 "깨졌는데 초록"이라는 **무감지**이고, 원인(버전 불일치)은 언제든 다시 생긴다. (1)만 하면 같은 일이 재발해도 못 알아차린다. (2)는 이미 Testcontainers로 구성된 테스트와 경로가 갈라져 비용이 크다.

## 4. 발견한 문제 (고치지 않음)
1. **Job Summary 문구가 부정확하다**: skipped==tests이면 "(Docker 없이 건너뜀)"이라고 쓴다(`ci.yml:63-64`). 실제로는 Docker 인식 실패일 수도 있다.
2. **skip을 막는 게이트가 없다.** 24-1은 가시성만 추가했고 실패 조건은 없다. 그래서 task 20/25-b/24-1에서 skip을 반복해 발견하고도 CI는 초록이다.
3. **25-b에서 해결 못 한 "Testcontainers ↔ Docker 29" 비호환이 미해결로 남아 있다.** 로컬 `~/.testcontainers.properties`의 `api.version=1.44`는 임시 우회일 뿐이고 저장소에는 반영돼 있지 않다.
4. decisions.md 2026-11-01/03/04 항목이 "CI에서는 실행될 것"이라는 전제를 담고 있다. CI에서 한 번도 실행되지 않았다면 이 전제는 사실이 아니다. 원인 확정 후 decisions.md 갱신 여부를 결정해야 한다(이번에는 수정하지 않음).
5. CI 실행 #15는 24-1에서 만든 요약으로 처음 드러난 것으로 보이며, 그 이전 실행은 같은 상태였을 가능성이 크다(추정).

## 5. 확인하지 못한 것
1. CI runner의 Docker Engine 버전과 Testcontainers의 실제 skip 사유(로그 접근 불가, `gh` 없음).
2. 현재 Docker 데몬을 켠 상태에서 로컬 실행 시 9개가 통과하는지(데몬이 꺼져 있어 못 돌렸다).
3. `DockerClientFactory.isDockerAvailable()`의 정확한 구현(라이브러리 소스는 이번에 열지 않았다).

## 6. 사용자가 다음에 할 일
1. Actions 실행 #15 → `test-reports` artifact의 `reports/tests` HTML에서 Postgres 클래스의 skip 메시지 확인(또는 로그에서 `Could not find a valid Docker environment` 검색).
2. 로컬 Docker Desktop을 켠 뒤 같은 명령을 다시 돌려 9개가 실행되는지 확인.
3. 추천안(1→3)을 진행할지 결정 → 별도 task로 구현.

## 7. 정리 (노션용)
1. **문제 상황**: CI가 초록인데 PostgreSQL 쿼리 검증 테스트 9개가 전부 skipped라 한 번도 실제로 실행되지 않았다.
2. **고려한 선택지**: ① Testcontainers를 CI에서 정상 동작시키기 ② CI에 Postgres service container ③ skip 시 CI 실패로 바꾸기.
3. **선택 이유**: ①+③ 권장. 원인(Docker 인식 실패 추정)을 고치고, 재발 시 초록으로 숨지 않게 게이트를 둔다. 트레이드오프: 버전 업/우회 설정 변경 비용, 로컬 Docker 없는 환경과의 정책 조정. 단 CI 원인은 로그를 못 봐서 추정이다.
4. **막혔던 부분과 해결**: CI 로그에 접근하지 못해 원인을 확정하지 못했다. 대신 어노테이션·워크플로·과거 task 기록으로 후보를 좁히고, 확정에 필요한 확인 방법을 남겼다.

## 8. 복습 질문

<details>
<summary>Q1. <code>@Testcontainers(disabledWithoutDocker = true)</code>는 어떤 경우에 테스트를 skip시키고, 왜 "Docker가 있는데도" skip될 수 있나요?</summary>

답:

</details>

<details>
<summary>Q2. 테스트가 전부 skipped인데 CI에 초록 체크가 뜨는 이유는 무엇이고, 이를 막으려면 어디에 무엇을 추가해야 하나요?</summary>

답:

</details>

<details>
<summary>Q3. (기존 코드 연결) <code>ContentRepositoryPostgresTest</code>는 <code>@DataJpaTest(replace = NONE)</code> + <code>@ServiceConnection</code>을 쓰고, 일반 Repository 테스트(<code>HighlightRepositoryTest</code>, <code>SchemaSqlSyncTest</code>)는 H2 test 프로파일을 씁니다. 이 구조에서 이 테스트가 skip되면 어떤 종류의 버그를 CI가 놓치게 되나요?</summary>

답:

</details>
