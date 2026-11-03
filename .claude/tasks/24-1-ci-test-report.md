# 24-1. CI에서 테스트 결과(실행/건너뜀 개수) 확인 가능하게 만들기

- 번호: `24-github-actions.md`가 있고, 선례(`21-1-email-unique.md`)와 같은 `N-1` 형태라 어긋나지 않음. 브랜치 `feat/auth-member`, 커밋하지 않음.
- 배경: task 24의 CI가 초록불로 통과했지만 로그/Job Summary에 테스트별 결과가 없어, Testcontainers 테스트(`ContentRepositoryPostgresTest`)가 CI에서 실제로 실행됐는지 알 수 없었다. 업로드 스텝도 `if: failure()`라 성공 시에는 리포트가 남지 않았다.
- 목표: CI 한 번으로 "총 몇 개 / skipped 몇 개 / 어떤 클래스가 실행됐는지"를 GitHub 화면에서 확인.

## 확인한 사실
- 기존 `ci.yml`: `defaults.run.working-directory: demo`는 `run` 스텝에만 적용된다. `uses`(upload-artifact)의 `path`는 working-directory의 영향을 받지 않아 레포 루트 기준(`demo/build/...`)이 맞다.
- XML 위치/형식(로컬 `./gradlew test` 후 확인): `demo/build/test-results/test/TEST-<FQCN>.xml`, 루트 `<testsuite name tests skipped failures errors>`. 중첩 클래스(`CorsConfigTest$CustomOrigins`)는 별도 파일이다. 로컬 집계: 파일 33개, tests 223, skipped 9, failures 0, errors 0. `ContentRepositoryPostgresTest`는 `tests="9" skipped="9"`.
- 스킵 조건: `@Testcontainers(disabledWithoutDocker = true)`는 Docker 사용 가능 여부를 클래스 실행 전에 검사해 불가능하면 클래스 전체를 skip한다. ubuntu-latest runner에는 Docker가 설치돼 있어 **실행될 것으로 예상**하나 이 PC에서는 확인할 수 없다 → **미확인**.

## 변경한 파일
| 파일 | 변경 |
|---|---|
| `.github/workflows/ci.yml` | `Test summary` 스텝 추가(`if: always()`), `Upload test reports`를 `if: failure()` → `if: always()` |
| `.claude/tasks/24-1-ci-test-report.md` | 신규 |
build.gradle, 테스트/앱 코드, application.yml, .gitignore, decisions.md는 수정하지 않았다.

## 설계 선택과 이유
1. **업로드를 `always()`로**: 성공 시에도 `build/test-results`(XML)와 `build/reports/tests`(HTML)를 artifact `test-reports`로 남긴다. 성공 run에서도 클래스별 skipped 증거를 내려받아 볼 수 있다. `failure()`는 Test 스텝이 실패했을 때만 도는 조건이다.
2. **Job Summary 스텝**: `python3` 인라인 스크립트(ubuntu runner 기본 설치)가 XML의 tests/skipped/failures/errors를 합산해 `$GITHUB_STEP_SUMMARY`와 로그에 출력하고, `ContentRepositoryPostgresTest`는 한 줄로 따로 보여 준다(전부 skipped면 "Docker 없이 건너뜀" 표시, XML이 없으면 "결과 XML 없음"). Test 스텝이 실패해도 요약이 나오게 `if: always()`.
   - 외부 액션을 추가하지 않았다(공급망 의존 최소화). 스크립트는 로컬에서 현재 XML로 실행해 위 집계(33개/223/9/0/0, Postgres 9 skipped)가 나오는 것을 확인했다.
3. **대안(채택 안 함)**
   - `build.gradle`의 `testLogging { events 'passed','skipped','failed' }`: 로그에 테스트별 줄이 찍히지만 build.gradle 수정이 필요하고 223줄이 로그를 길게 만든다. 이번 범위 밖이라 후속 후보로만 기록.
   - `dorny/test-reporter` 등 리포트 액션: 체크 탭에 예쁘게 나오지만 외부 액션 + 추가 권한(`checks: write`)이 필요하다.
   - 로그에서 `--info` 출력으로 확인: 노이즈가 많고 구조화되지 않는다.

## 검증 (실제 결과)
- 로컬 `./gradlew test --rerun-tasks`(demo/): 종료 코드 0(BUILD SUCCESSFUL). XML 집계 223개 실행 대상, 실패 0, 에러 0, skipped 9(전부 `ContentRepositoryPostgresTest`, 이 PC에서 Docker 데몬이 응답하지 않아 skip).
- `ci.yml`: Python `yaml.safe_load`로 파싱 성공, 스텝 순서(`Test` → `Test summary` → `Upload test reports`)와 `always()` 조건 확인. 요약 스크립트는 YAML에서 추출해 로컬에서 실행해 출력 확인(콘솔 인코딩 때문에 한글이 깨져 보였으나 로직 결과는 정상). actionlint는 설치되어 있지 않아 YAML 스키마/표현식 검증은 육안 검토만 했다.

## 직접 확인하지 못한 것
1. GitHub에서 실제 실행: Summary 표시, artifact 업로드, `GITHUB_STEP_SUMMARY` 기록.
2. CI에서 `ContentRepositoryPostgresTest`가 실제로 실행되는지(skipped=0, 통과 여부) — 미확인.
3. runner의 python3 존재와 UTF-8 출력(ubuntu-latest 기본 환경이라 문제 없을 것으로 보나 실행해 보지 않음).
4. `$GITHUB_STEP_SUMMARY` 마크다운 렌더링 모양.

## 발견한 문제 / 후속 후보 (고치지 않음)
- build.gradle `testLogging` 추가(위 대안).
- task 24에서 이어진 것: `demo/gradlew` 실행 비트(100644), 테스트 폴더의 `DemoApplication` 중복, 빈 `injectMocks.java`.
- 이번 범위 밖으로 기록만: `actions/setup-java` 버전 업그레이드 검토, 캐시/timeout 설정 재검토.
- `Test summary`는 XML 경로 `build/test-results/test/`를 하드코딩했다. Gradle 기본 경로이므로 build.gradle에서 test task 설정을 바꾸면 깨진다.
- 이전 세션의 `C:\Users\tooth47348\tmp_test.log`가 아직 남아 있다(직접 삭제 필요).

## 사용자가 해야 할 일
1. 커밋·push 후 Actions → 해당 run → 맨 아래 **Summary 탭의 "Test summary"**에서 `ContentRepositoryPostgresTest: tests=9, skipped=0`인지 확인한다. skipped=9면 runner에서 Docker가 안 잡힌 것이다.
2. 같은 run 하단 **Artifacts의 `test-reports`**를 내려받아 `test-results/test/TEST-...ContentRepositoryPostgresTest.xml`을 열어 `<skipped/>`가 없는지 본다.
3. 실패(failures>0)면 PostgreSQL 전용 쿼리가 처음 실제 엔진에서 돈 결과일 수 있으니 XML의 메시지를 task 파일에 가져와 다음 task로 분리한다.

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: CI가 초록불이어도 어떤 테스트가 실행됐는지 알 수 없었고, 특히 Docker가 없으면 조용히 skip되는 테스트가 CI에서 돌았는지 검증할 방법이 없었다.
2. **고려한 선택지**: ① 리포트 artifact를 항상 업로드 ② XML 합계를 Job Summary에 출력 ③ `testLogging` 추가 ④ 외부 리포트 액션.
3. **선택 이유**: ①+②. build.gradle 수정과 외부 액션 없이 skipped 수를 바로 볼 수 있다. 트레이드오프: 스크립트가 Gradle XML 경로에 의존하고, 테스트별 상세 로그는 artifact를 열어야 한다.
4. **막혔던 부분과 해결**: `working-directory`는 `run`에만 적용되고 `uses`의 `path`에는 적용되지 않아 artifact 경로는 레포 루트 기준을 유지. 초록불은 "skip 포함 통과"라 `if: failure()`로는 성공 시 증거가 남지 않으므로 `always()`로 바꿨다. 워크플로 문자열을 스크립트로 옮길 때 이스케이프된 `\n`이 실제 개행으로 들어가 YAML이 깨졌고, `chr(10)`으로 바꿔 해결했다.

## 복습 질문
(토글 안에 답을 적어주세요. 프론트 질문은 없다.)

<details><summary>Q1. 테스트 빌드가 초록불(성공)이어도 "그 테스트가 실행됐다"는 증거가 되지 않는 이유는 무엇인가요?</summary>

(여기에 답)

</details>

<details><summary>Q2. 업로드·요약 스텝에 `if: always()`가 필요한 이유는 무엇이고, `if: failure()`와 무엇이 다른가요?</summary>

(여기에 답)

</details>

<details><summary>Q3. (기존 코드 연결) `ContentRepositoryPostgresTest`의 `@Testcontainers(disabledWithoutDocker = true)`는 CI에서 어떻게 동작하나요? 일반 DB 테스트(H2, `application-test.yml`)와 이 테스트는 CI에서 각각 어떤 DB를 쓰나요?</summary>

(여기에 답)

</details>
