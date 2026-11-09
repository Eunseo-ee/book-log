# 25-a-1. EnvironmentBindingTest가 실행 환경의 환경변수에 오염되는 문제 (CI 실패 수정)

- 번호: `N-하위번호-이름` 선례(21-1, 25-d-1)를 따라 25-a 바로 뒤 후속으로 `25-a-1`. 커밋하지 않음.
- 목표: CI(`./gradlew test --no-daemon`)에서 334개 중 1개만 실패하는 `EnvironmentBindingTest`를 실행 환경과 무관하게(hermetic) 만든다. 운영 코드, 25-a 결정(환경변수 이름, `@NotBlank`)은 건드리지 않는다.

## 현황
- 실패: `EnvironmentBindingTest > 키가 하나라도 없거나 공백이면 시작이 실패하고...` `EnvironmentBindingTest.java:64` (`assertThat(ctx).hasFailed()`). 로컬 통과, CI 실패.
- 테스트 구성(수정 전): `withEnv(...)`가 `TestPropertyValues.of(vars).applyTo(env, Type.SYSTEM_ENVIRONMENT)`로 값을 넣는다. 이 방식은 이름이 `systemEnvironment`인 **기존 소스(실제 OS 환경변수)에 값을 덧붙인다**(25-a에서 소스 이름을 맞춘 이유와 같은 계열).
- 64번째 줄은 `withEnv("API_TMDBTOKEN=t")`로 "kakaoKey 없음" 상황을 만들고 기동 실패를 기대한다.
- ci.yml이 주입하는 환경변수: `API_KAKAOKEY`, `API_TMDBTOKEN` 두 개뿐(`JWT_*` 등은 주입하지 않음).

## 원인 (가설 확인됨)
CI 러너의 진짜 `API_KAKAOKEY`가 덧붙인 소스 안에 그대로 남아, "kakao 키 없음" 상황에서도 키가 채워져 기동이 성공한다 → "실패해야 한다"는 단언이 깨진다. 같은 이유로 72번째 줄의 `runner.run(...)`(환경변수 없음)도 실제 환경변수에 오염될 상태였다(64번째 줄에서 먼저 실패해 도달하지 않았을 뿐).

### 로컬 재현 (수정 전)
- `API_KAKAOKEY=fake-kakao`, `API_TMDBTOKEN=fake-tmdb`(가짜 값)을 설정하고 `./gradlew cleanTest test` 실행: **334개 중 1개 실패, skipped 9**, 실패 위치도 `EnvironmentBindingTest.java:64`로 CI와 동일.
- 환경변수 없이 실행하면 같은 테스트가 통과(로컬에서 계속 통과했던 이유).

## 변경 내용
| 파일 | 변경 |
|---|---|
| `demo/src/test/java/book_log/demo/config/EnvironmentBindingTest.java` | `isolateSystemEnvironment` 추가: `systemEnvironment` 소스를 테스트가 만든 값만 가진 `SystemEnvironmentPropertySource`로 **교체**(replace). `withEnv`와 `jwtNames`가 이를 사용. 72번째 줄의 `runner.run`은 `withEnv()`(빈 환경)로 변경. 불필요해진 `TestPropertyValues` import 제거 |

운영 코드, ci.yml, application*.yml, 다른 테스트는 건드리지 않았다.

## 결정과 이유
- 테스트 코드에서 격리한다(ci.yml 시크릿 제거 같은 우회 안 함). 문제는 운영 코드가 아니라 테스트가 실행 환경에 의존한 것이고, 교체 방식은 CI/로컬/개발자 PC의 환경변수가 무엇이든 결과가 같다. 이름을 `systemEnvironment`로 유지해 relaxed binding(25-a에서 확인한 이름 변환)은 그대로 탄다.
- 이미 같은 방식(replace)을 쓰는 `CorsOriginsTest`, `EnvironmentBindingTest.envOf`와 일치시켰다.

## 막힌 부분과 해결
- 검증 중 환경변수 있는 상태의 전체 테스트에서 41개가 실패했는데, 내가 CI에 없는 `JWT_SECRET=fake`까지 설정했기 때문이었다(재현 실수). CI와 같게 `API_*` 두 개만 설정하고 다시 돌려 통과를 확인했다. 이 실수는 아래 "발견한 문제 1"의 증거가 되었다.
- Gradle은 환경변수를 테스트 입력으로 보지 않아 `test`만 다시 돌리면 UP-TO-DATE가 되므로 `cleanTest`를 붙였다.

## 발견한 문제 (고치지 않음)
1. **실제 환경변수에 의존하는 다른 테스트**: `@SpringBootTest` 계열(`DemoApplicationTests`, `ProdExposureTest`, `LocalSwaggerTest`, `ProdProfileDefaultTest` 등)은 실제 OS 환경변수가 `application-test.yml`보다 우선한다. 증거: 이번에 `JWT_SECRET=fake`를 설정하자 41개가 실패했다. CI는 `API_*`만 주입해 값이 있는 한 가짜 값을 덮어써도 통과하지만, 시크릿이 없어 **빈 문자열**이면 `@NotBlank`로 컨텍스트 로딩이 실패할 수 있다(25-a 발견 3, 이번에도 미검증). 개발자 PC에 `SPRING_DATASOURCE_*`, `SPRING_PROFILES_ACTIVE` 등이 있어도 영향받을 수 있다.
2. `HikariEnvBindingTest`: `TestPropertyValues(SYSTEM_ENVIRONMENT)`로 덧붙이는 같은 방식. 실제 환경에 `SPRING_DATASOURCE_*`가 있으면 테스트 값과 섞일 수 있다(지금 값 3개는 모두 테스트가 덮어씀).
3. **skipped 9**: CI에서도 `ContentRepositoryPostgresTest` 9개가 skipped로 로컬과 같다. GitHub 러너에는 보통 Docker가 있는데 건너뛴 이유는 분석하지 않았다(25-b에서 Testcontainers 1.20.4가 Docker 29를 인식 못 한 사례가 있음. 범위 밖).

## 테스트 결과 (실행한 것)
| 조건 | 결과 |
|---|---|
| 수정 전 + `API_KAKAOKEY`/`API_TMDBTOKEN` 가짜 값 | **FAILED** 334개 중 실패 1 (EnvironmentBindingTest:64), skipped 9 |
| 수정 후 + 같은 가짜 값(CI와 동일) | BUILD SUCCESSFUL, 334개, 실패 0, 에러 0, skipped 9 |
| 수정 후 + 환경변수 없음 | BUILD SUCCESSFUL, 334개, 실패 0, 에러 0, skipped 9 |
push 후 실제 CI 통과 여부는 확인하지 않았다(사용자 확인).

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 로컬에서 통과하던 환경변수 바인딩 테스트가 CI에서만 실패했다. 테스트는 "키 없음" 상황을 만든다고 믿었지만, CI가 주입한 진짜 환경변수가 그대로 남아 있었다.
2. **고려한 선택지**: ① 테스트에서 `systemEnvironment` 소스를 통째로 교체(채택) ② ci.yml에서 해당 env 제거/비우기 ③ 테스트 전에 실제 환경변수를 지우도록 JVM/Gradle 설정.
3. **선택 이유**: ①. 원인이 테스트 격리 부족이라 테스트에서 고치는 게 맞고, 어떤 환경에서도 같은 결과가 나온다. ②는 CI만 통과시킬 뿐 개발자 PC의 환경변수에는 여전히 취약하고 CI에서 키 주입 경로를 검증할 방법이 없어진다. ③은 OS 환경변수를 JVM 안에서 바꿀 수 없어 Gradle `environment` 설정이 필요하고 테스트 밖에 규칙이 숨는다. 트레이드오프: 테스트가 실제 `systemEnvironment` 병합 동작은 보지 않는다(이름 변환만 검증).
4. **막혔던 부분과 해결**: 재현 때 CI에 없는 `JWT_SECRET`까지 넣어 41개가 실패해 헷갈렸다. CI와 같은 변수만 넣어 재현해 정확히 1개만 실패함을 확인했다. Gradle 테스트 UP-TO-DATE는 `cleanTest`로 피했다.

## 복습 질문
<details><summary>Q1. 수정 전 테스트가 CI에서만 실패한 이유를 `TestPropertyValues`의 `SYSTEM_ENVIRONMENT` 동작(덧붙이기)과 연결해 설명하라.</summary>

(여기에 답)

</details>

<details><summary>Q2. ci.yml에서 시크릿 env를 지우는 대신 테스트 쪽 소스를 교체(replace)하기로 한 이유와 트레이드오프는?</summary>

(여기에 답)

</details>

<details><summary>Q3. (기존 코드 연결, 25-a) 25-a에서 환경변수 소스 이름을 `systemEnvironment`로 맞춰야 했던 이유는 무엇이고, 이번 교체 방식은 그 이유를 어떻게 지키나?</summary>

(여기에 답)

</details>
