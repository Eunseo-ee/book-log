# 35. 예외 응답 정리 (입력 오류 400 / 없는 리소스 404 / 생성 201 + Location)

- 브랜치: `main`. 커밋 1개("예외 응답 정리").
- 산출물: GlobalExceptionHandler 핸들러 4개, HighlightService/HighlightController 수정, `ExceptionResponseTest`(신규), 기존 테스트 3곳 수정, decisions 24·39번 갱신 + 신규 항목.
- 진행: 먼저 코드 수정 없이 현재 응답을 MockMvc로 기록(`ExceptionResponseBaselineTest`) → 수정안 승인 → 적용 → 같은 케이스를 단언 테스트(`ExceptionResponseTest`)로 전환.

## 수정 전후 상태 코드
| # | 요청 | 전 | 후 |
|---|---|---|---|
| 1a | `GET /api/calendar/activities?year=2026&month=13` | 500 (ConstraintViolationException) | 400, message에 `month` |
| 1b | `GET /api/calendar/activities?year=1999&month=4` | 500 (ConstraintViolationException) | 400, message에 `year` |
| 2 | `GET /api/search?category=잘못된값&query=abc` | 500 (MethodArgumentTypeMismatchException) | 400, 입력값 에코 없음 |
| 3 | `GET /api/statistics?month=13` | 400 | 400 (변경 없음) |
| 4 | `POST /api/highlights` 없는 contentId | 400 (IllegalArgumentException) | 404 (ResourceNotFoundException) |
| 5 | `POST /api/highlights` 성공 | 200, Location 없음, body `55` | 201, `Location: /api/highlights/55`, body `55` |
| 6 | `GET /api/search?category=BOOK` (query 누락) | 500 (MissingServletRequestParameterException) | 400 |
| 7 | `POST /api/contents` 깨진 JSON | 500 (HttpMessageNotReadableException) | 400, body 내용 에코 없음 |
| 8 | `GET /api/calendar/activities?year=abc&month=4` | (측정 안 함) | 400, 입력값 에코 없음 (타입 변환 핸들러의 부수 효과를 테스트로 고정) |

6·7은 "500이 확인되면 추가, 아니면 건드리지 않는다"는 조건으로 먼저 측정했고 둘 다 500이어서 핸들러를 추가했다. 8은 수정 전에 측정하지 않았다(추정이 아니라 수정 후 테스트로만 확인).

## 프론트 영향
- 200에 의존하는 곳 없음. `frontend/lib/api.ts`의 `request()`는 `!res.ok`로 실패를 판단하고 204만 따로 처리한다. 201은 ok이고 `res.json()`이 숫자 body를 읽는다.
- `AddHighlightModal.tsx`는 `saveHighlight`의 반환값을 쓰지 않는다. 실패 시 `toErrorMessages`가 body의 `message`를 보여주므로 404/400 모두 같은 경로.
- 프론트 테스트 파일 없음.

## 테스트 결과 (실제 실행)
- `./gradlew test --rerun-tasks`: **BUILD SUCCESSFUL, 372개, 실패 0, 에러 0, skipped 0.** `ContentRepositoryPostgresTest` 9개도 skip 없이 통과.
- 첫 실행에서 1건 실패했다(아래 막혔던 부분).

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: 클라이언트 입력 오류인데 서버 오류(500)로 나가는 요청이 있었다(캘린더 `@Min/@Max` 위반, 잘못된 Enum 값, 필수 파라미터 누락, 깨진 JSON). 없는 contentId는 "없는 리소스"인데 400이었고, 생성 API(하이라이트 POST)는 200이었다.
2. **고려한 선택지**: ① `IllegalArgumentException`을 넓게 400으로 두고 계속 재사용 ② 예외별 핸들러를 추가해 상태 코드를 의미대로 매핑 ③ `@Validated`를 빼고 서비스에서 직접 검증.
3. **선택 이유**: ②. ①은 "입력 오류"와 "없는 리소스"가 같은 400이라 구분이 안 되고 서비스 내부의 잘못된 인자까지 400으로 삼킨다. ③은 컨트롤러 어노테이션을 걷어내는 변경이 크다. 트레이드오프는 핸들러가 4개 늘고, 메시지는 파라미터 이름과 고정 문구만 쓰기 때문에 사용자가 보는 설명이 덜 구체적이라는 점(입력값을 에코하면 파서/검증 메시지에 입력 조각이 새서 일부러 뺐다).
4. **막혔던 부분과 해결**: 전체 테스트에서 `HighlightServiceTest.saveHighlight_Fail_TooLongText`가 실패했다. 이 테스트는 `contentRepository.findById(1L)`을 stub하지 않아 mock이 `Optional.empty()`를 반환했고, 서비스가 "없는 콘텐츠"로 던진 `IllegalArgumentException`에 우연히 맞아 통과해 왔다. 글자 수 검증은 서비스가 아니라 컨트롤러 `@Valid`의 일이라 테스트 이름과 실제 검증 대상이 달랐다. 승인된 2곳(NoContent, MemberIdPropagation) 외 3번째 수정이며, 의도는 바꾸지 않고 기대 예외 타입만 `ResourceNotFoundException`으로 바꾼 뒤 주석을 남겼다.

## 한계 (문서화만)
- Location(`/api/highlights/{id}`)은 PUT/DELETE 경로를 가리킨다. 단건 `GET /api/highlights/{id}`는 없다. `/api/contents/{id}`도 동일.
- `@Min/@Max` 위반 메시지의 제약 문구는 서버 로케일 기본 메시지다(테스트는 필드 이름 포함 여부만 확인).

## 발견 사항 (고치지 않고 기록만)
1. `HighlightServiceTest.saveHighlight_Fail_TooLongText`는 길이 검증을 테스트하지 않는다(서비스에는 길이 검사 없음, 컨트롤러 `@Valid`가 담당). 이름과 내용이 맞는 테스트로 바꾸거나 지울지 별도 판단 필요.
2. `DateTimeException` 핸들러는 어디서 던져져도 "월은 1-12 사이여야 합니다" 고정 메시지를 반환한다(이전 task에서 발견, 그대로).
3. 다른 예외도 500으로 샐 수 있다(예: 지원하지 않는 HTTP 메서드/Content-Type, 경로 변수 타입 변환 등은 일부 처리되지만 전체는 확인하지 않음). 이번에는 확인한 4종만 처리했다.
4. 기존 `IllegalArgumentException`→400 핸들러는 그대로라 서비스의 다른 `IllegalArgumentException`(하이라이트 year 범위 등)은 계속 400이다. 좁힐지는 별도 판단.

## 복습 질문
(토글 안에 답을 적어주세요. 프론트 질문은 답을 함께 적었습니다.)

<details><summary>Q1. 캘린더 `month=13`이 수정 전에 500이었던 이유를, 예외 이름과 `GlobalExceptionHandler`의 구조로 설명해보세요.</summary>

(여기에 답)

</details>

<details><summary>Q2. 없는 contentId로 하이라이트를 저장할 때 400이 아니라 404가 맞다고 판단한 기준은 무엇이고, 이 선택의 트레이드오프(기존 ForbiddenResourceException 403과 비교)는 무엇인가요?</summary>

(여기에 답)

</details>

<details><summary>Q3. (기존 코드 연결) `StatisticsController`의 month 검증은 `DateTimeException`을 직접 던지고 `CalendarController`는 `@Validated`로 검증합니다. 같은 "월 범위 오류"를 두 방식이 다르게 처리하는데, 이번 수정 후 두 경우의 응답은 어떻게 같고 어떻게 다른가요?</summary>

(여기에 답)

</details>

<details><summary>Q4. (프론트) 하이라이트 저장 응답이 200에서 201로 바뀌었는데 프론트 코드는 왜 수정이 필요 없었나요?</summary>

**답**: `frontend/lib/api.ts`의 `request()`가 상태 코드를 숫자로 비교하지 않고 `res.ok`(200~299)로 성공/실패를 판단하기 때문이다. 201도 ok이고, 성공이면 `res.json()`으로 body(저장된 id 숫자)를 읽는다. 204만 `undefined`로 따로 처리한다. 저장 후 `AddHighlightModal`은 반환값을 쓰지 않고 `onSaved()`만 호출한다. 같은 이유로 400이던 응답이 404가 돼도 `toErrorMessages`가 body의 `message`를 읽어 같은 문구를 보여준다.

</details>
