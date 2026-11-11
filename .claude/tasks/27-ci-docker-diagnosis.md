# 27. CI Postgres 테스트 skip 원인 확정용 진단 스텝 + Testcontainers 업그레이드 조사

- 번호: 직전이 `26`이라 `27`. 커밋하지 않음.
- 범위: ci.yml에 임시 진단 스텝 추가, 업그레이드 **조사만**, 가드 **설계만**. Testcontainers 버전/build.gradle/테스트 코드/decisions.md는 수정하지 않았다.

## 1. 변경 내용
- `.github/workflows/ci.yml`: `Test` 스텝 바로 앞에 `Docker 버전 확인 (임시 진단)` 스텝 추가. `docker --version`, `docker version`, `docker info`를 실행하고 각 명령에 `|| true`, 스텝에 `continue-on-error: true`를 둬 실패해도 워크플로가 깨지지 않는다. **원인 확인 후 이 스텝과 위 주석을 제거한다.**
- 이 task 파일 추가(26 파일은 그대로).

## 2. 조사 결과

### 2-1. 현재 버전과 선언 위치 (확인함)
- `demo/build.gradle:57-59`: `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`. 버전 명시 없음(주석: Boot BOM이 관리).
- Spring Boot 플러그인 3.4.1 (`build.gradle:3`) → Testcontainers 1.20.4 (task 20, 25-b에서 확인한 값. 이번에 `dependencies` 출력으로 재확인하지는 않음).
- `ext['lombok.version']`이 이미 `build.gradle:28`에서 BOM 오버라이드에 쓰이고 있어 같은 방식이 이 프로젝트에 이미 있다.

### 2-2. Boot BOM이 관리하고 올리는 방법
- 관리한다: Boot 3.4 [dependency-versions 부록](https://docs.spring.io/spring-boot/3.4/appendix/dependency-versions/properties.html)에 `testcontainers.version` 프로퍼티가 있다(검색 결과로 확인).
- 올리는 법: `build.gradle`에 `ext['testcontainers.version'] = '<버전>'`. ([Spring Kafka 문서](https://docs.spring.io/spring-kafka/reference/appendix/override-boot-dependencies.html)의 `ext['kafka.version']` 예시를 같은 방식으로 적용한 것 — **Testcontainers에 대한 직접 예시는 못 찾았다, 추정**.)
- **주의(추정, 미확인)**: Testcontainers 2.x는 아티팩트 이름(`testcontainers-postgresql`, `testcontainers-junit-jupiter` 등)과 패키지가 바뀐 것으로 알고 있으나 이번에 공식 문서로 확인하지 못했다. Boot 3.4의 `spring-boot-testcontainers`는 1.x 기준으로 컴파일돼 있어, ext만으로 2.x로 올리면 깨질 수 있다. 2.x는 Boot 4 쪽이 정석일 가능성이 높다. 별도 task에서 실제로 의존성 해석·컴파일해서 확인해야 한다.

### 2-3. Docker 29.x 호환 후보 (출처 있는 것 / 미확인)
- 원인: docker-java 기본 API 버전이 1.32로, Docker 29의 최소 지원 API보다 낮아 협상 실패. 로컬에서 `docker version`이 "최소 API 1.40"으로 보인 것과도 모순 없음(1.32 < 1.40). 출처: [DEV Community 글](https://dev.to/romanpinchuk/docker-engine-v29-fixing-api-version-mismatch-error-15j6), [Spring Boot 이슈 #48104](https://github.com/spring-projects/spring-boot/issues/48104), [asyncapi PR](https://gh.ali360.cloudns.ch/asyncapi/java-spring-template/pull/456) (검색 결과 목록만 보고 본문을 직접 열어 보진 않음).
- 후보 A: **Testcontainers 2.0.2 이상** — 2차 자료(블로그/일본어 글)가 2.0.2에서 수정됐다고 주장. **공식 릴리스 노트는 미확인.**
- 후보 B: 1.x 패치 — Spring Boot 이슈 쪽 정보로는 1.x 라인은 수정 릴리스가 없는 것 같다는 보고. **미확인**(1.21.x에 수정이 있는지는 확인 못 함).
- 우회(업그레이드 아님): `docker-java.properties`에 `api.version=1.44` (`src/test/resources/`에 두면 저장소에 반영 가능 — 단 이번 방침상 "근본 해결(업그레이드) 우선"이라 비추천), 데몬 `min-api-version` 낮추기(CI에서는 비현실적).
- 버전 뉘앙스(2차 자료, 미확인): Docker 29.0~29.2는 최소 API 1.44, 29.3부터 1.40. 어느 쪽이든 docker-java 기본 1.32보다 높다.
- **확인 필요**: 공식 [testcontainers-java 릴리스 페이지](https://github.com/testcontainers/testcontainers-java/releases)에서 "Docker 29"/"API version" 관련 항목, 그리고 1.21.x/2.0.x가 Boot 3.4.1과 함께 해석·컴파일되는지.

## 3. skip 가드 설계안 (적용 안 함)
| 후보 | 방법 | 장점 | 단점 |
|---|---|---|---|
| G1 | ci.yml `Test summary`의 python에서 `ContentRepositoryPostgresTest`의 skipped>0 (또는 XML 없음)이면 `sys.exit(1)` | 테스트 코드 무변경, 로컬 개발자 영향 없음, 대상 클래스를 명시해 의도가 분명 | 클래스 이름이 문자열로 박혀 이름 변경 시 조용히 깨짐(XML 없음도 실패 처리로 완화). Test 스텝 뒤라 원인 로그가 이미 남아 있음(장점이기도 함) |
| G2 | JUnit 조건 변경: `disabledWithoutDocker`를 빼고 CI(`CI` env)에서는 Docker 없으면 실패하게 | 실패 지점이 테스트에서 바로 나와 원인 메시지가 직접 보임 | 테스트 코드 수정 필요, 로컬 Docker 없는 환경과 정책 분기 코드가 생김 |
| G3 | Gradle test 태스크에서 해당 클래스 skip 시 실패(TestListener) | 로컬/CI 동일 규칙 | build.gradle 수정, 로컬 Docker 꺼진 개발자도 실패 → 마찰 큼 |
- **추천: G1.** 이유: 변경이 CI 한 곳이고 로컬에 영향이 없으며, 문제의 본질(초록인데 안 돌았음)을 정확히 겨냥한다. 단 **업그레이드로 CI에서 실제 통과하게 된 뒤에 켠다**(지금 켜면 CI가 빨개짐). 같은 때 Summary의 "(Docker 없이 건너뜀)" 문구도 "Docker 인식 실패 가능"으로 정정(26의 발견 1).

## 4. 후속 확인 항목 (기록만)
1. 테스트 Postgres 이미지(`postgres:17-alpine`, `ContentRepositoryPostgresTest.java:47`) vs Supabase 실제 버전: Supabase SQL editor에서 `select version();` 확인 필요.
2. 코드 주석은 "15로 가정"이라 하는데 이미지는 17 → 불일치. decisions.md 2026-11-01 항목은 `postgres:15-alpine`이라고 적혀 있어 이것도 낡았다(수정하지 않음).
3. 업그레이드 task에서 할 일: 2-2/2-3의 미확인 사항 확정 → ext 오버라이드 → 로컬 `~/.testcontainers.properties`의 `api.version` 우회 제거 후 9개 통과 확인 → 진단 스텝 제거 → G1 적용 → decisions.md 갱신.

## 5. 검증
- YAML: python `yaml.safe_load`로 ci.yml 파싱 성공, 스텝 순서 `Make gradlew executable → Docker 버전 확인 (임시 진단) → Test → Test summary → Upload`. (콘솔 인코딩 때문에 한글 이름이 깨져 보였을 뿐 파싱은 정상)
- `cd demo && ./gradlew test --no-daemon` 실제 실행: **BUILD SUCCESSFUL**, 결과 XML 합계 tests 334 / skipped 9 / failures 0 / errors 0. `ContentRepositoryPostgresTest` tests=9, skipped=9. 이 PC는 Docker 데몬이 꺼져 있어(26 기록) skip이 정상이다.
- CI에서 진단 스텝의 실제 동작: **미확인(push 후 확인 필요).**

## 6. 사용자가 push 후 확인할 것
Actions → test job → `Docker 버전 확인 (임시 진단)` 로그에서:
1. `Client:` / `Server:`의 **Version**과 **API version**
2. Server 쪽 **Minimum API version** (이 값이 1.32보다 크면 가설 확정)
3. `docker info` 자체가 실패/Server 누락이면 "Docker 없음" 쪽(후보 A)
확인 결과를 알려 주면 26의 2-4 표를 확정한다.

## 7. 정리 (노션용)
1. **문제 상황**: CI에서 Postgres 테스트 9개가 전부 skip되는데 원인을 로그로 확인하지 못했다.
2. **고려한 선택지**: ① 진단 스텝으로 원인부터 확정 ② 바로 우회 설정(`api.version`) 추가 ③ 바로 업그레이드.
3. **선택 이유**: ①. 원인을 추정으로 두고 ②③을 하면 헛수고 가능성이 있고, 우회는 로컬/CI 이중 부채가 된다. 트레이드오프: CI 한 번 더 돌려야 한다. 업그레이드는 BOM 오버라이드(2.x는 아티팩트/호환 미확인)라 별도 task.
4. **막혔던 부분과 해결**: 공식 릴리스 노트를 직접 확인하지 못해 호환 버전을 단정할 수 없었다. 확인된 것(프로퍼티 존재)과 2차 자료 기반 추정을 구분해 "미확인"으로 표기했다.

## 8. 복습 질문

<details>
<summary>Q1. Docker가 실행 중인데도 <code>disabledWithoutDocker = true</code> 테스트가 skip될 수 있는 이유는? (Docker API 버전 협상 관점)</summary>

답:

</details>

<details>
<summary>Q2. 진단 스텝에 <code>continue-on-error: true</code>와 <code>|| true</code>를 둘 다 둔 이유는, 그리고 이 스텝이 `Test` 스텝보다 앞에 있어야 하는 이유는?</summary>

답:

</details>

<details>
<summary>Q3. (기존 코드 연결) H2 test 프로파일로 도는 Repository 테스트들은 이 skip과 무관하게 초록인데, 그럼에도 `ContentRepositoryPostgresTest`가 필요한 이유는? 어떤 쿼리가 H2로는 검증되지 않나?</summary>

답:

</details>
