# 29. CI 진단 스텝 제거 + Postgres 테스트 skip 가드

- 번호: 직전이 `28`이라 `29`. 커밋하지 않음.
- 범위: `.github/workflows/ci.yml`, task 28 기록 보강, decisions.md 항목 2개 추가. 버전/테스트 코드/Postgres 이미지/로컬 설정 파일은 건드리지 않았다.

## 1. 변경 내용
- ci.yml: `Docker 버전 확인 (임시 진단)` 스텝과 위 주석 제거. 스텝 순서는 `Make gradlew executable → Test → Test summary → Upload`.
- Test summary 문구: `(Docker 없이 건너뜀)` → `(skip됨: Docker 연결 실패 가능성, Test 로그 확인)`. 조건도 `s == n` 에서 `s > 0`으로 넓혀 일부만 skip돼도 표시.
- task 28에 CI 검증 결과 9절 추가(추정 → 확인된 사실 정리, 미확인은 유지).
- decisions.md에 항목 2개 추가(Testcontainers 1.21.4 채택, skip 시 CI 실패 정책).

## 2. 가드 동작 방식 (27의 G1)
Test summary 스크립트에서 **요약을 stdout과 `GITHUB_STEP_SUMMARY`에 먼저 쓰고**, 그 다음에 `ContentRepositoryPostgresTest`의 XML이 없거나 skipped>0이면 `::error::` 로그를 찍고 `sys.exit(1)`. 스텝에 `if: always()`가 있어 Test가 실패해도 실행되고, 그 경우 XML이 없으면 가드도 실패하지만 이미 빨간 상태라 영향 없다. Upload 스텝도 `always()`라 가드 실패 후에도 리포트는 올라간다.

## 3. 검증 결과
- YAML: `yaml.safe_load` 성공, 스텝 목록에서 진단 스텝 사라짐 확인.
- 가드 스크립트를 YAML에서 추출해 로컬 `demo/build/test-results/test/*.xml` 복사본(임시 폴더)으로 시뮬레이션, 끝나고 임시 폴더 삭제(레포 변경은 ci.yml뿐):
  | 경우 | 입력 | exit code |
  |---|---|---|
  | (a) 정상 | Postgres 클래스 tests=9 skipped=0 (XML 수정본) | **0** |
  | (b) skip | 실제 로컬 결과 tests=9 skipped=9 | **1** (요약 출력 후 `::error::`) |
  | (c) XML 없음 | Postgres XML 삭제 | **1** (`결과 XML 없음` 출력 후 `::error::`) |
  (b, c에서도 요약이 가드보다 먼저 출력됨.)
- `./gradlew test` (이 PC는 Docker 꺼짐): BUILD SUCCESSFUL. Postgres 테스트 9개는 skip된다. 새 가드는 **CI의 Test summary 스텝에만** 있어 로컬 gradle 실행에는 영향이 없다.
- **CI에서의 실제 동작: 미확인(push 후 확인 필요).**

## 4. push 후 확인할 것
1. Actions 로그에 `Docker 버전 확인 (임시 진단)` 스텝이 없는지.
2. Test summary에 `ContentRepositoryPostgresTest: tests=9, skipped=0`이고 job이 초록인지.
3. (선택) 가드가 진짜 실패하는지는 일부러 확인하지 않아도 됨(3절 시뮬레이션으로 갈음). 확인하려면 임시 브랜치에서 테스트에 `@Disabled`를 붙여 보면 되지만 이번엔 하지 않음.

## 5. 후속 항목 (기록만, 수정 안 함)
1. **해결됨(2026-11-11)** 테스트 이미지 vs Supabase 실제 버전: `select version();` 결과 PostgreSQL 17.6 (aarch64-unknown-linux-gnu, gcc 15.2.0). 이미지 `postgres:17-alpine`과 메이저 일치, 이미지 변경 불필요. (확인 방법은 사용자가 SQL 편집기에서 실행한 결과를 전달받은 것)
2. **해결됨(2026-11-11)** 코드 주석 "15로 가정"과 이미지 17 불일치: 주석을 17.6 기준으로 정정(`demo/src/test/java/book_log/demo/repository/ContentRepositoryPostgresTest.java` 주석 한 줄, 코드 무변경), decisions.md 2026-11-01 항목의 `postgres:15-alpine` 문구를 17로 정정하고 2026-11-11 항목(메이저만 맞추고 마이너 미고정) 추가.
3. 로컬 `~/.docker-java.properties`(api.version 우회)가 이제 불필요한지: Docker Desktop을 켜고 `mv ~/.docker-java.properties ~/.docker-java.properties.bak` → `cd demo && ./gradlew test --tests '*ContentRepositoryPostgresTest'` → 결과 XML에서 tests=9 skipped=0 확인 → 원래 이름으로 복구(통과하면 파일 삭제 여부는 사용자 판단).
4. 가드는 클래스 이름 문자열 의존: 테스트 클래스 이름을 바꾸면 가드가 "XML 없음"으로 실패해 알려준다(의도된 동작).
5. 26의 발견 1(Summary 문구)은 이번에 해소.

## 6. 정리 (노션용)
1. **문제 상황**: Postgres 테스트가 전부 skip돼도 CI가 초록이라, 검증이 안 돌고 있다는 사실이 오래 가려졌다.
2. **고려한 선택지**: ① Summary 스크립트에서 skipped>0/XML 없음이면 실패 ② 테스트의 `disabledWithoutDocker` 제거 ③ Gradle TestListener.
3. **선택 이유**: ①. 변경이 CI 한 곳이고 테스트 코드·로컬 개발자에게 영향이 없다. 트레이드오프: 클래스 이름이 문자열로 박힘, 실패 지점이 Test 스텝이 아니라 summary 스텝.
4. **막혔던 부분과 해결**: 가드가 실패해도 요약이 남아야 해서 "요약 기록 → 가드" 순서로 두고, Test가 실패해 XML이 없는 경우도 같은 분기로 처리되게 했다.

## 7. 복습 질문

<details>
<summary>Q1. skip된 테스트가 CI에서 초록 체크로 보이는 이유는? 그리고 이 가드가 Test 스텝이 아니라 Test summary 스텝에 있는 이유는?</summary>

답:

</details>

<details>
<summary>Q2. 가드를 "skipped>0"뿐 아니라 "결과 XML 없음"까지 실패로 잡은 이유는? 요약을 가드보다 먼저 쓴 이유는?</summary>

답:

</details>

<details>
<summary>Q3. (기존 코드 연결) H2 test 프로파일 테스트와 ContentRepositoryPostgresTest는 각각 무엇을 검증하나? 이 가드가 없으면 어느 쪽의 공백이 생기나?</summary>

답:

</details>
