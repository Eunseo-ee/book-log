# 18. 하이라이트 소유권 검증 (save/update/delete)

- 브랜치: `feat/auth-member` (시작 시 `git status` 깨끗함, 마지막 커밋 `a2c6528` task 17)
- 참고: `.claude/decisions.md` 2026-10-28 항목(task 17), `.claude/tasks/13-member-auth.md`, `14-jwt-auth.md` (이 저장소에는 `15`~`17` task 파일이 없어 14번 형식을 따랐다. 번호 18은 지시대로 사용)
- 커밋하지 않았다. 인증/JWT/SecurityConfig, 조회·통계·캘린더, 인덱스는 건드리지 않았다.

## 배경
task 17에서 조회에는 memberId 조건을 걸었지만 하이라이트 save/update/delete는 소유자 검증이 없어, 남의 콘텐츠에 하이라이트를 달거나 남의 하이라이트를 수정/삭제할 수 있었다.

## 설계
| 항목 | 내용 |
|---|---|
| 시그니처 | `saveHighlight(memberId, dto)`, `updateHighlight(memberId, id, dto)`, `deleteHighlight(memberId, id)` |
| save | contentId로 content 조회(없으면 기존 `IllegalArgumentException` 400) → 소유자 불일치면 403 |
| update/delete | `findById`(없으면 404) → `highlight.content.member.id` 비교(불일치 403) → 수정/삭제. delete는 `deleteById` 대신 `delete(entity)` |
| 소유자 비교 | private `validateOwner(content, memberId)` 하나로 세 곳이 공유. `content.getMember().getId()`만 호출(member LAZY 프록시, 초기화·추가 쿼리 없음) |
| 예외 | `ForbiddenResourceException`(403, 고정 메시지), `ResourceNotFoundException`(404) 신규. 기존에 재사용할 403/404 예외는 없었음. `GlobalExceptionHandler`에 핸들러 2개 추가만(기존 핸들러 변경 없음), 응답은 기존 `ErrorResponse(status, message)` |
| update의 contentId | 서비스가 요청의 contentId를 읽지 않음(원래 동작). 이동이 안 되는 것을 테스트로 고정하고 서비스에 주석 |
| 컨트롤러 | 임시 `memberId = 1L` 유지. save/update/delete 3곳에 추가(아래 task 19 TODO) |

## 선택과 이유
1. **403 vs 404:** 남의 것에 403을 주면 "존재하지만 권한 없음"이 드러나 id 열거가 가능하다. 둘 다 404로 통일하면 숨길 수 있지만 클라이언트가 원인을 구분하지 못한다. 이번엔 체크리스트대로 403/404 구분(backlog에 재검토 기록).
2. **서비스에서 검증:** 컨트롤러가 task 19에서 바뀌어도 규칙이 유지되고 mock 기반 단위 테스트로 검증 가능.
3. **delete 전 조회:** `deleteById`는 없는 id를 조용히 무시한다(404 불가, 소유자 모름). 비용은 SELECT 1회 추가.

## 테스트
- `./gradlew test` 전체 **163개 통과, 실패 0, 스킵 0** (테스트 결과 XML 합산)
- `HighlightServiceTest` 11개 통과: save(내 것 성공 / 남의 content 403+save 호출 안 됨 / 없는 content 기존 동작) update(성공 / contentId 달라도 이동 안 함 / 403 내용 그대로 / 404) delete(성공 / 403+delete·deleteById 호출 안 됨 / 404) + 기존 길이 테스트
- `GlobalExceptionHandlerOwnershipTest` 3개 통과(`@WebMvcTest`, DB 없음): 403·404가 `{status, message}` 형식, IllegalArgumentException은 여전히 400
- 컨트롤러 → 서비스 실제 연동, 실제 DB의 소유권 쿼리 동작은 테스트하지 않았다(서비스는 mock 리포지토리).

## 변경 파일
- 수정: `service/HighlightService.java`, `controller/HighlightController.java`, `exception/GlobalExceptionHandler.java`, 테스트 `service/HighlightServiceTest.java`, `.claude/decisions.md`·`backlog.md`(추가만)
- 신규: `exception/ForbiddenResourceException.java`, `exception/ResourceNotFoundException.java`, 테스트 `exception/GlobalExceptionHandlerOwnershipTest.java`, 이 파일

## 남은 TODO (task 19)
`HighlightController`의 임시 `memberId = 1L`을 `@AuthenticationPrincipal AuthenticatedMember`로 교체할 위치: `getHighlights`(task 17), **`save`, `update`, `delete`(이번에 추가, 총 4곳)**. 다른 컨트롤러의 임시값(Calendar, Content, Search, Statistics)은 기존 목록 그대로.

## 발견 사항 (고치지 않음)
1. update/delete는 highlight 조회 후 `content`(LAZY)를 한 번 더 조회해 쿼리 2회. 대량 호출이 아니라 방치, backlog 기록.
2. 기존 `saveHighlight_Fail_TooLongText`는 서비스에 길이 검증이 없고 mock이 빈 Optional을 돌려줘서 우연히 통과한다(의도한 검증 아님).
3. 하이라이트 id 순차 노출 + 403/404 구분으로 id 열거 가능(선택 1).
4. 이 저장소에는 `tasks/15`~`17` 파일이 없다(decisions.md와 커밋 기록에만 있음).
