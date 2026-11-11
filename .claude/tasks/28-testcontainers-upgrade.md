# 28. Testcontainers 1.21.4 업그레이드 (Docker 29 호환)

- 번호: 직전이 `27`이라 `28`. 커밋하지 않음.
- 범위: `demo/build.gradle` 한 줄 추가. ci.yml(진단 스텝 포함), 테스트 코드, skip 가드, Postgres 이미지는 건드리지 않았다.

## 1. 변경 내용
- `demo/build.gradle`: `ext['testcontainers.version'] = '1.21.4'` 추가 (기존 `ext['lombok.version']` 아래, 같은 BOM 오버라이드 방식).
- 해석 결과 확인(`dependencies --configuration testRuntimeClasspath`): `testcontainers`, `postgresql`, `junit-jupiter`, `database-commons`, `jdbc` 모두 1.21.4 (`1.20.4 -> 1.21.4`). docker-java는 **3.4.2 그대로**.

## 2. 버전 선택과 근거
| 후보 | 근거 | 판단 |
|---|---|---|
| 2.0.2 이상 | 공식 릴리스 페이지 확인: 2.0.2(2025-11-13) "기본 Docker API 버전 1.44로 설정", 2.0.3은 구버전 엔진용 1.32 폴백. 2.0.0에서 아티팩트 이름이 `testcontainers-postgresql` 등으로 바뀌고 컨테이너 클래스가 `org.testcontainers.<module>` 패키지로 이동 | **제외.** 테스트 코드가 `org.testcontainers.containers.PostgreSQLContainer`를 쓰는데 테스트 수정은 범위 밖이고, Boot 3.4.1의 `spring-boot-testcontainers`도 1.x 클래스 기준이라 `@ServiceConnection`이 깨질 가능성이 높다(후자는 직접 컴파일해 보진 않음, **추정**) |
| 1.21.4 | 공식 릴리스 노트(2025-12-15) 한 줄: "makes version 1.21.x works with recent Docker Engine changes". Docker 29나 API 버전이라는 말은 **노트에 없음**. 1.x 중 엔진 변경 대응을 명시한 유일한 릴리스 | **선택** |
| 1.21.1~1.21.3 | 노트에 엔진 대응 언급 없음 | 제외 |
- 트레이드오프: 1.x 마지막 라인이라 장기적으론 Boot 4 / Testcontainers 2.x로 가야 한다. 지금은 코드 변경 없이 마이너 업그레이드만 한다.
- **미확인**: 1.21.4가 실제로 어떤 방식(기본 API 버전 상향 등)으로 Docker 29 거절을 고쳤는지. 노트에 상세가 없고 docker-java가 3.4.2 그대로라 코드로도 확인하지 못했다. 즉 "1.21.4면 해결된다"는 **공식 노트의 한 줄에 기댄 것이고 아직 실행으로 검증되지 않았다.** 27의 "원리는 알려진 사례 기준" 항목도 여전히 코드로 직접 확인하지 못했다.

## 3. 검증 결과
- **Docker 데몬이 꺼져 있어(npipe `dockerDesktopLinuxEngine` 없음) 컨테이너 검증은 못 했다: 미확인.**
  - 우회 파일 `~/.docker-java.properties`는 존재만 확인했고 **이름을 바꾸지 않았다**(원래 이름 그대로).
  - `ContentRepositoryPostgresTest` 9개가 실제 passed인지: **미확인.**
- `./gradlew test`(데몬 꺼진 상태) 실행: BUILD SUCCESSFUL, XML 합계 tests 334 / skipped 9 / failures 0 / errors 0. skipped 9는 전부 `ContentRepositoryPostgresTest`(Docker 없음으로 skip). 즉 **기존 테스트는 1.21.4에서도 컴파일·통과함**(H2 계열 325개). 이 결과는 업그레이드 성공의 증거가 아니다.

## 4. 직접 해 볼 것 (Docker Desktop 켠 뒤)
1. `docker version`으로 Server API / Minimum API 확인.
2. 우회 해제: `mv ~/.docker-java.properties ~/.docker-java.properties.bak`
3. `cd demo && ./gradlew test --tests '*ContentRepositoryPostgresTest'` → `build/test-results/test/TEST-...PostgresTest.xml`에서 tests=9 skipped=0 확인.
4. `./gradlew test` 전체. 끝나면 `.bak`을 원래 이름으로 복구.
5. 3에서 여전히 skipped=9면 1.21.4로는 부족한 것: 그때 선택지는 (a) 2.x + Boot 4 이관, (b) `src/test/resources/docker-java.properties`의 `api.version` 우회(부채로 확정했던 방향이라 사용자 판단 필요).

## 5. push 후 확인할 것
- Actions Test summary에서 `ContentRepositoryPostgresTest` skipped=0, tests=9.
- 진단 스텝(`Docker 버전 확인 (임시 진단)`)은 아직 유지. 통과 확인 뒤 다음 task에서 제거하고 G1 가드 적용, decisions.md 갱신.

## 6. 발견 사항 (수정 안 함)
- decisions.md에 이번 업그레이드 항목은 **추가하지 않았다**: 검증 전이라 확정 결정으로 쓰기 이르다. 통과 확인 후 추가.
- `ContentRepositoryPostgresTest.java:46` 주석 "15로 가정" vs 이미지 `postgres:17-alpine` 불일치(27에서 이미 기록).

## 7. 정리 (노션용)
1. **문제 상황**: Docker 29 환경에서 Testcontainers 1.20.4가 데몬에 거절돼 Postgres 테스트 9개가 조용히 skip.
2. **고려한 선택지**: ① 2.0.2+ ② 1.21.4 ③ `api.version` 우회 파일.
3. **선택 이유**: ②. ①은 아티팩트/패키지 변경으로 테스트 코드와 `@ServiceConnection` 호환이 깨질 수 있고, ③은 근본 해결이 아니라 부채. 트레이드오프: 1.21.4의 수정 내용은 릴리스 노트가 한 줄뿐이라 실행 검증 전까지 확정이 아님.
4. **막혔던 부분과 해결**: 공식 노트에 Docker 29 언급이 없어 "해결"을 단정하지 못했고, 로컬 Docker가 꺼져 있어 검증도 못 했다. 단정하지 않고 검증 절차(4번)를 남겼다.

## 8. 복습 질문

<details>
<summary>Q1. <code>ext['testcontainers.version']</code> 한 줄로 <code>org.testcontainers:postgresql</code>, <code>junit-jupiter</code>의 버전이 모두 바뀌는 이유는? 버전이 없는 선언이 어떻게 해석되나?</summary>

답:

</details>

<details>
<summary>Q2. Docker 클라이언트(docker-java)와 데몬의 API 버전 협상이 어떻게 실패해 테스트가 에러가 아니라 skip이 되나? (<code>disabledWithoutDocker</code> 관점)</summary>

답:

</details>

<details>
<summary>Q3. (기존 코드 연결) <code>@DataJpaTest</code>에 <code>replace = NONE</code>을 주고 <code>@ServiceConnection</code>을 쓰면 DB URL은 어떻게 컨테이너로 연결되나? <code>replace</code>를 기본값으로 두면 무슨 일이 생기나?</summary>

답:

</details>
