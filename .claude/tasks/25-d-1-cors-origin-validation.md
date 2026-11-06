# 25-d-1. CORS origin 형식 검증 + 정리

- 번호: 25-d 바로 뒤 후속(`N-하위번호-이름` 선례: 21-1, 24-1). 커밋하지 않음.
- 목표: 25-d 발견 5번(스킴 없는 origin이 조용히 아무 요청도 매치하지 않음) 해결 + 25-d 발견 2·3번(env 예제 중복, tsbuildinfo 추적) 정리.
- 범위 밖: 25-e(Swagger/actuator), 25-f(배포), 25-g(외부 API 방어).

## 작업 전 현황
- `CorsOrigins.parse`: 분리 → trim → 끝 `/` 제거 → 빈 값 제거 → 중복 제거 → `*` 검사. **형식 검사 없음**이라 `myapp.vercel.app`, `https://a.com/path`가 그대로 허용 목록에 들어갔다. Spring `CorsConfiguration.checkOrigin`은 요청 Origin 헤더와 **문자열 비교**하므로 이런 값은 기동은 되지만 어떤 요청과도 일치하지 않아 프론트만 CORS로 실패한다.
- 프론트 `frontend/.env.local.example`(값 `http://localhost:8080`, 주석 1줄)과 `frontend/.env.example`(25-d, 값 비움, 설명 주석 다수) 공존. `frontend/tsconfig.tsbuildinfo`는 git 추적 중이고 `.gitignore`에 없었다.

## 결정 사항
1. **검증 형식**: 정규식 `^https?://[^/?#@\s]+$`. 스킴은 소문자 `http`/`https`만(브라우저 Origin 헤더 스킴이 소문자이고 비교가 대소문자 구분이라 `HTTPS://`는 영원히 불일치), 호스트[:포트]에는 `/ ? # @`와 공백이 없어야 한다.
2. **경로/쿼리/프래그먼트 거부 — 적용.** Origin 헤더는 스킴+호스트+포트뿐이라 `https://a.com/path`는 어떤 요청과도 매치하지 않는다. 자동으로 잘라 주면 오타를 조용히 고쳐 의도를 숨기고, 거부하면 설정 실수를 기동 시점에 바로 알 수 있다. 사용자정보(`user@`)도 같은 이유로 거부. 끝 슬래시만은 25-d 규칙대로 제거하고 허용(`https://a.com/`, 흔한 복붙 실수). 단 `https://a.com/path/`는 슬래시를 떼도 `/path`가 남아 거부된다.
3. **검증 순서**: 정리(trim, 끝 `/` 제거, 빈 값 무시, 중복 제거) **뒤에** 각 값에 대해 `*` 검사 → 형식 검사. 이유: ① 정리 전에 검사하면 ` https://a.com/ `이나 끝 슬래시 같은 정상 입력이 형식 오류가 된다 ② `*` 검사는 형식 검사와 별도로 필요하다: `https://*.vercel.app`은 스킴이 맞고 호스트 자리에 `*`가 허용 문자라 형식 검사를 통과해 버린다. 먼저 검사하면 `*` 단독 같은 값에도 "와일드카드 금지"라는 더 구체적인 메시지를 줄 수 있다. ③ 빈 값(`,,`)은 에러가 아니라 무시라 기존 prod "빈 값이면 기동 실패" 흐름이 그대로 유지된다.
4. **메시지**: 문제의 값 하나만 포함하고(다른 origin이나 다른 설정 값은 넣지 않음) 테스트(`hasMessageNotContaining`)로 고정.
5. **파일 정리**: `tsconfig.tsbuildinfo`는 `.gitignore`에 `*.tsbuildinfo` 추가(Next가 만드는 증분 캐시라 어느 위치든 무시) + `git rm --cached`(파일은 디스크에 남김). env 예제는 **`frontend/.env.example`을 남긴다.** 기준: ① Next.js 컨벤션은 커밋하는 템플릿을 `.env.example`로 두고 실제 로컬 값은 gitignore된 `.env.local`에 둔다(`.env.local.example`은 컨벤션 이름이 아님, 이 저장소 루트도 `.env.example`) ② 25-d에서 만든 `.env.example`이 `.env.local.example`의 내용(주소 의미)을 이미 포함하고 production 필수/dev 기본값/시크릿 금지 설명이 더 많다 ③ 두 파일의 변수는 `NEXT_PUBLIC_API_URL` 하나로 같다. 합친 결과의 변경은 없다(이미 상위 집합). 다만 `.env.local.example`의 값 `http://localhost:8080`은 `.env.example` 주석에 "dev에서 비면 http://localhost:8080"으로 이미 적혀 있다.

### 지울 파일 목록 (삭제하지 않음, 사용자 확인 후)
- `frontend/.env.local.example`

## 변경 파일
| 파일 | 내용 |
|---|---|
| `config/CorsOrigins.java` | 형식 정규식과 검사 추가 |
| `test/.../CorsOriginsTest.java` | 테스트 4개 추가 |
| `.gitignore` | `*.tsbuildinfo` 추가 |
| git 인덱스 | `frontend/tsconfig.tsbuildinfo` 추적 제거(`git rm --cached`) |

SecurityConfig, 기존 CORS 테스트, application*.yml, Dockerfile은 건드리지 않았다.

## 추가한 테스트 (4개, CorsOriginsTest)
| 테스트 | 확인 |
|---|---|
| validMixedValues | `http://localhost:3000, https://app.example.com/` → 통과, 슬래시 제거 |
| rejectsMissingScheme | `myapp.vercel.app`, `localhost:3000`, `//a`, `ftp://`, `HTTPS://`, `https://` 거부. 메시지에 잘못된 값만 있고 같이 넣은 정상 값은 없음 |
| rejectsPathQueryFragment | `/path`, `/path/`, `?x=1`, `#top`, `user@`, 공백/탭 포함 거부. `https://a.com///`는 통과 |
| invalidFromEnvFails | 환경변수로 넣은 스킴 없는 값이 CORS 설정 생성(기동) 실패 |

## 검증 결과 (실행한 것만)
- `./gradlew cleanTest test`: BUILD SUCCESSFUL, **248개, 실패 0, 에러 0, skipped 9**(244 + 신규 4). skipped 9는 이전과 같은 `ContentRepositoryPostgresTest`(Testcontainers가 Docker를 인식 못 함, 통과 미확인 상태 그대로). 기존 `CorsConfigTest` 등 CORS 테스트는 수정 없이 통과.
- 프론트: `NEXT_PUBLIC_API_URL=https://api.example.test/ npm run build` 성공, `npx tsc --noEmit` 통과. (이번 변경은 프론트 코드가 아니라 git 설정/추적 정리뿐이다.)
- `git status`: `frontend/tsconfig.tsbuildinfo`가 삭제(인덱스에서 제거)로 표시되고 파일은 디스크에 남음. 빌드 후 다시 생겨도 무시됨.

## 막힌 부분과 해결
- 처음 자바 소스에 정규식이 `\s`(단일 백슬래시)로 들어가 **공백 문자 하나만** 막고 탭 등은 통과시켰다. 탭을 넣은 테스트가 실패해서 발견했고 `\s`로 고쳤다.

## 발견한 문제 (고치지 않음)
1. 호스트 자체의 유효성(`https://exa mple`처럼 공백은 막지만 `https://..`, 잘못된 포트, 한글 도메인 등)은 검증하지 않는다. 형식(스킴+경로 없음)까지만 본다.
2. IPv6 origin(`http://[::1]:3000`)은 정규식을 통과하지만 별도로 테스트하지 않았다.
3. 루트 `.env.example`의 `SPRING_PROFILES_ACTIVE=`가 값이 비어 있으면 Spring이 빈 프로파일로 처리하는지는 확인하지 않았다(25-f에서 값을 넣는 것이 전제).
4. (25-d 발견 이월) 프론트 lint 없음, 비허용 origin 403 본문 형식, `SPRING_PROFILES_ACTIVE=prod` 미지정 시 보호 꺼짐.

## 남은 TODO
- `frontend/.env.local.example` 삭제 여부 확인.
- 25-f 체크리스트: `SPRING_PROFILES_ACTIVE=prod`, origin 값은 `https://호스트` 형식.

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: `APP_CORS_ALLOWED_ORIGINS`에 스킴을 빠뜨리거나 경로를 붙여도 앱은 정상 기동하고 health도 200이지만, 브라우저 Origin 헤더와 문자열이 달라 모든 직접 호출이 CORS로 실패한다. 서버 로그에는 원인이 안 남는다.
2. **고려한 선택지**: ① 형식이 틀리면 기동 실패(채택) ② 경고 로그만 남기고 계속 ③ 자동 보정(스킴 붙이기, 경로 자르기).
3. **선택 이유**: ①. 설정 실수는 배포 직후 가장 싸게 발견할 수 있고 25-a, 25-d의 "누락/오류는 기동에서 드러낸다" 방침과 같다. ③은 의도를 추측하다 보안상 의도하지 않은 origin을 열 수 있다(http/https 중 어느 쪽인지 알 수 없음). 트레이드오프: 사소한 오타에도 배포가 실패한다.
4. **막혔던 부분과 해결**: 정규식 `\s` 이스케이프 실수(위). 정리(슬래시 제거) 후 검증하는 순서로 정상 입력의 오탐을 피했다.

## 복습 질문
<details><summary>Q1. origin 형식 검증을 요청 처리 시점이 아니라 기동 시점에 하는 이유는? 잘못된 값을 자동으로 고쳐 주지 않고 거부한 이유도 설명하라.</summary>

(여기에 답)

</details>

<details><summary>Q2. (기존 코드 연결, task 19/25-a) 25-a의 ApiConfig는 `@Validated + @NotBlank`로 키 누락 시 기동 실패를 만든다. 이번 CorsOrigins 검증은 그것과 같은 목적을 어떤 방식(어디서, 어떤 예외)으로 달성하고, 둘의 차이는 무엇인가? 또 prod 프로파일에서 빈 값 기동 실패는 형식 검증과 어떻게 이어지나?</summary>

(여기에 답)

</details>
