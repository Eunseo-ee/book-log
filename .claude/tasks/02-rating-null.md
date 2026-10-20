# 02. 평점(rating) 미입력 저장 허용 (backend)

- 브랜치: `feat/search-ui`
- 참고: `.claude/decisions.md` (2026-10-20 평점은 선택 입력), `.claude/tasks/01-search-frontend.md` 발견 사항

## 목표
평점(rating)을 비우고 저장해도 서버가 500이 아니라 정상 저장(201)되게 한다.
평점은 선택 입력이다 (WANT_to_WATCH 상태 등 평점이 없는 기록이 정상 시나리오).

## 현재 문제
`ContentService.saveContent`에서 `requestDto.getRating()`을 null 체크 없이 비교해서,
rating이 없으면 NPE → `GlobalExceptionHandler`의 최후 핸들러로 500이 나간다.

## 구현 요구사항
1. rating이 null이면 범위 검증을 건너뛰고 저장한다. null이 아니면 기존처럼 0~5 범위를 검증한다.
2. 저장된 Content의 rating은 0.0이 아니라 null로 남아야 한다 (0점과 "평가 안 함"을 구분).
3. 같은 가정(rating은 non-null)에 의존하는 다른 코드를 조사해 보고한다. 수정은 하지 않는다.
   - 조사 대상: StatisticsService, ContentRepository 통계 쿼리, ContentResponseDto, Content.updateRating, frontend의 rating 표시부
   - 통계 평균이 null 행을 제외하고 계산되는지 쿼리 기준으로 확인 (AVG 동작)

## 테스트
- 추가: rating이 null인 요청이 저장 성공하고 Content.rating이 null로 전달되는지 (Mockito)
- 기존 rating 검증 테스트(-1.0, 10.0)와 미래 날짜 테스트가 그대로 통과하는지 확인
- `./gradlew test` 결과 보고

## 수정 허용 범위
`demo/src/main/java/.../service/ContentService.java`, `demo/src/test/.../service/ContentServiceTest.java`만.
조사에서 문제가 발견되면 고치지 말고 "발견 사항"에 적기만 한다.

## 범위 밖
TMDB 저자/감독 문자열, currentPage, 인증, 프론트 코드, 다른 서비스 로직, viewDate 필수 제약(별도 백로그)

---

## 구현 내용
- `ContentService.saveContent`: `rating`을 지역 변수로 받아 `rating != null && (rating < 0 || rating > 5.0)`일 때만 예외를 던진다. 바로 아래 미래 날짜 검증(`getViewDate() != null && ...`)과 같은 null 가드 방식이다.
- 엔티티 변환(`toEntity`)은 그대로 둔다. `Content.rating`이 래퍼 타입 `Double`이라 null이 그대로 들어간다. 기본값 0.0으로 채우는 코드는 없다.
- 테스트 `saveContent_Success_NullRating`: rating 없는 DTO를 저장한다. `ArgumentCaptor`로 `save()`에 넘어간 Content를 잡아 `getRating()`이 null인지 확인한다.
  - 수정 전 코드로 돌려보면 이 테스트는 `NullPointerException`으로 실패한다(직접 확인함). 수정 후에는 통과한다.

## 조사 결과 (rating non-null 가정 의존 여부)
| 대상 | 결과 |
|---|---|
| `ContentRepository.getAverageRating` | `SELECT AVG(c.rating) ...`. SQL `AVG`는 NULL 행을 **분자와 분모 모두에서 제외**한다. 따라서 평점 없는 기록은 0점으로 계산되지 않는다. 예: 4.0, 2.0, NULL → 3.0 (0점으로 셌다면 2.0) |
| `StatisticsService` (47~48행) | `rawAvgRating != null ? 반올림 : 0.0`. 해당 월에 평점 있는 행이 하나도 없으면 AVG가 NULL을 반환하고, 이때 응답은 `0.0`이 된다. NPE는 없다. 단, 아래 발견 사항 1 참고 |
| `countByMonth`, 카테고리/장르 집계 | `COUNT(c)`는 행 수를 센다. 평점 없는 기록도 활동량에 포함된다. 의도에 맞는 동작이다 |
| `ContentResponseDto` | `Double rating = entity.getRating()`. 언박싱이 없어서 null을 그대로 전달한다. `JsonInclude(NON_NULL)` 설정이 없으므로 JSON에는 `"rating": null`로 나간다(코드 기준). 프론트 타입 `rating: number \| null`과 일치한다 |
| `Content.updateRating` | null을 받아도 그대로 대입하므로 문제가 없다. 다만 범위 검증이 없고, 현재 호출하는 곳도 없다(발견 사항 2) |
| `ContentRequestDto` `@Min(0) @Max(5)` | Bean Validation은 null을 유효한 값으로 본다. 그래서 null은 컨트롤러 `@Valid` 단계를 통과한다. -1, 10 같은 값은 서비스까지 오기 전에 `@Valid`에서 400(필드 에러 맵)으로 막힌다 |
| DB 컬럼 | `ddl-auto: update`이고 `@Column(nullable=false)`가 없다. 그래서 nullable 컬럼으로 만들어졌을 것이다. 실제 Supabase 테이블 제약은 **미확인**(서버/DB 필요) |
| frontend | `CalendarView.tsx:81` 평균 별점: `stats.averageRating ? ... : '—'`. `ContentResponseDto.rating`을 화면에 표시하는 컴포넌트는 현재 없다. 저장 폼은 비우면 키를 보내지 않는다(`SaveContentForm.tsx:68`) |

## 검증 결과
### 서버 없이 확인한 것
- [x] `./gradlew test`: **57개 전부 통과**(실패 0, 에러 0, 스킵 0). 기존 56개 + 새 테스트 1개. `ContentServiceTest` 8개
- [x] 기존 `saveContent_Fail_Rating_Low`(-1.0), `saveContent_Fail_Rating`(10.0), `saveContent_Fail_FutureDate` 그대로 통과
- [x] 새 테스트가 수정 전 코드에서 NPE로 실패하는 것 확인(수정 사항 임시 stash 후 실행, 다시 복원)
- [x] AVG의 NULL 제외 동작: 쿼리 코드와 SQL 표준 동작 기준으로 확인. **실제 DB 실행은 아님**

### 서버가 필요한 것 (시나리오)
1. **평점 없이 저장 → 201**
   - `POST /api/contents`에 `{"title":"테스트","externalId":"x-1","category":"MOVIE","viewDate":"<오늘>","status":"WANT_to_WATCH"}`처럼 rating 키 없이 보낸다.
   - 기대 결과: `201 Created`, `Location: /api/contents/{id}`. DB에서 `SELECT rating FROM content WHERE id={id}` → `NULL`(0이 아님)
   - 프론트에서는 '평점 없음'을 선택하고 저장했을 때 모달이 닫히고 '저장됨'으로 바뀌는지 확인
2. **평점 없는 항목이 월별 통계 평균에 0점으로 들어가지 않는지**
   - 같은 달(예: 이번 달)에 rating 4.0인 기록 1개와 rating 없는 기록 1개를 저장한다(그 달에 다른 기록이 없는 상태에서 시작하거나, 기존 평균을 먼저 기록해둔다).
   - `GET` 월별 통계 API(StatisticsController, 해당 year/month)를 호출한다.
   - 기대 결과: `averageRating = 4.0`(0점으로 셌다면 2.0), `activity.currentCount = 2`(평점 없는 기록도 활동량에는 포함)

## 변경 파일 (커밋하지 않음)
- 수정: `demo/src/main/java/book_log/demo/service/ContentService.java`
- 수정: `demo/src/test/java/book_log/demo/service/ContentServiceTest.java`
- 추가(작업 기록): `.claude/tasks/02-rating-null.md`
- `.claude/backlog.md`, `.claude/decisions.md`는 작업 전부터 수정되어 있었고, 이번 작업에서 건드리지 않았다.

## 발견 사항 (수정하지 않음)
1. **"평점 없음"과 "평균 0점"이 통계 응답에서 구분되지 않는다.** 그 달에 평점이 하나도 없으면 서버가 `0.0`을 내려준다(`StatisticsService.java:48`). 프론트는 `0`을 falsy로 보고 `'—'`를 표시한다(`CalendarView.tsx:81`). 그래서 지금은 "평점 없음"이 우연히 '—'로 맞게 보인다. 반대로 실제 평균이 0.0점인 달도 '—'로 보인다. 서버가 null을 내리고 프론트가 `!= null`로 검사하도록 바꿀지는 별도 판단이 필요하다(프론트 타입은 이미 `number | null`).
2. **`Content.updateRating`에는 범위 검증이 없다.** 현재 호출하는 곳은 없다. 하지만 수정 API를 만들 때 0~5 검증과 null 허용 규칙을 저장 쪽과 맞춰야 한다. 검증을 엔티티나 공용 메서드로 모을지 결정이 필요하다.
3. **서버는 0.5 단위를 강제하지 않는다.** `@Min/@Max`와 서비스 검증 모두 범위만 본다(4.7 허용). 0.5 단위는 프론트 select로만 보장된다.
4. **같은 범위 검증이 두 군데에 있다.** 컨트롤러 경로에서는 `@Min(0) @Max(5)`가 먼저 400(필드 에러 맵 형식)을 낸다. 서비스 검증(`ErrorResponse` 형식)은 서비스를 직접 호출할 때만 동작한다. 그래서 같은 "범위 초과" 오류라도 응답 형식이 경로마다 다르다.

---

## 정리 (트러블슈팅 기록용)
1. **문제 상황**
   평점은 선택 입력인데, 평점을 비우고 저장하면 500이 났다. `requestDto.getRating() < 0`에서 `Double`이 `double`로 자동 언박싱되면서 null이면 NPE가 났다. 이 예외를 최후 핸들러(`Exception`)가 잡아 500으로 응답했다.
2. **고려한 선택지**
   - A. 서비스 검증에 null 가드만 추가(`rating != null && ...`)
   - B. null이면 0.0으로 바꿔서 저장
   - C. 서비스 검증을 지우고 DTO의 `@Min/@Max`(null 허용)에만 맡김
3. **선택 이유 (트레이드오프)**
   A를 골랐다. B는 NPE는 막지만 "0점"과 "평가 안 함"이 섞여 통계 평균이 내려간다. AVG가 NULL을 빼주는 이점도 잃는다. C는 코드가 가장 줄지만 서비스를 직접 호출하는 경로(테스트, 향후 다른 호출부)의 검증이 사라진다. 기존 -1/10 테스트도 깨진다. A는 변경이 한 줄이고, 바로 아래 viewDate 검증과 같은 패턴이다.
4. **막혔던 부분과 해결**
   - 테스트가 "예외가 안 났다"만 보면 B 방식(0.0 대입)도 통과한다. 그래서 `ArgumentCaptor`로 `save()`에 실제로 넘어간 엔티티의 rating이 null인지 검사했다.
   - 수정 전 코드에서 새 테스트가 실제로 실패하는지(NPE) 따로 돌려서, 테스트가 버그를 잡는다는 것을 확인했다.

> 개념 메모
> - **자동 언박싱 NPE**: `Double`(객체)을 `<`, `>` 같은 산술·비교 연산에 쓰면 Java가 `doubleValue()`를 몰래 호출한다. 값이 null이면 이 호출에서 NPE가 난다. 래퍼 타입은 null 가능성을 표현하려고 쓰는 것이므로, 연산 전에 null부터 확인해야 한다.
> - **SQL 집계와 NULL**: `AVG`, `SUM`, `COUNT(컬럼)`은 NULL 행을 건너뛴다. `COUNT(*)`/`COUNT(엔티티)`는 행 수를 센다. 그래서 "평가 안 함"을 0이 아니라 NULL로 저장해야 평균이 왜곡되지 않는다. 대상 행이 전부 NULL이면 AVG 결과 자체가 NULL이다.

## 복습 질문
1. `requestDto.getRating() < 0`이 rating이 null일 때 NPE를 내는 이유를 "언박싱" 관점에서 설명하고, 왜 400이 아니라 500이 나갔는지 `GlobalExceptionHandler` 기준으로 말해보라.
2. 평점 없는 기록을 null 대신 0.0으로 저장했다면 `AVG(c.rating)` 결과는 어떻게 달라지는가? 4.0점 1개 + 평점 없음 1개를 예로 들어보라.
3. (기존 코드 연결) rating이 -1인 요청이 `POST /api/contents`로 들어오면 서비스의 범위 검증까지 도달하는가? `@Valid` + `@Min(0)`과 `MethodArgumentNotValidException` 핸들러 기준으로 설명하고, 응답 본문 형식이 서비스 검증 실패 때와 어떻게 다른지 비교해보라.
