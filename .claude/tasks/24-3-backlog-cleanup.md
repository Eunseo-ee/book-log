# 24-3. backlog.md 정리 (문서 작업)

- 번호: 24, 24-1, 24-2 다음이라 어긋나지 않음. task 25는 쓰지 않았다.
- 성격: 문서 정리만. 수정한 파일은 `.claude/backlog.md`와 이 파일뿐이다. 코드·설정·워크플로·다른 task 파일·decisions.md는 수정하지 않았고 커밋하지 않았다.
- 배경: 24-2 감사 F-8 — 해결된 항목이 전부 `[ ]`로 남아 진행 현황 판단에 쓸 수 없었다.
- 방식: 항목마다 현재 코드(파일/줄)와 task/decisions 번호를 근거로 닫힘/열림을 판정했다. 근거를 못 찾으면 닫지 않았다. 항목은 삭제하지 않았고, 문구는 그대로 두고 근거를 줄 끝에 덧붙였다. 섹션 3개로 순서만 나눴다: "열림 - 배포 전", "열림 - 나중에", "해결됨".
- 실행한 것: 코드/문서 읽기와 grep뿐. 앱 기동, 브라우저, `./gradlew test`는 실행하지 않았다(문서만 바꿈).

## 결과
| 구분 | 개수 |
|---|---|
| 전체 | 50 (원래 50, 삭제 없음) |
| 닫힘 `[x]` | 12 |
| 열림 `[ ]` — 배포 전 | 6 |
| 열림 `[ ]` — 나중에 | 32 |

### 닫은 12개와 근거
| 항목 | 근거 |
|---|---|
| isSaved가 userId 미반영 | task 17, `ContentService.findSavedKeys` → `findByMemberIdAndExternalIdIn` |
| 배지 라벨 통일 | task 01, `CATEGORY_LABEL` 공용 상수. 프론트에 "도서"/"TV 프로그램" 문자열 없음 |
| GET /api/highlights 없음 | task 06, `HighlightController.getHighlights` |
| 하이라이트 직접 호출 CORS + GET 없음 | task 06(GET) + 08(전역 CORS, `SecurityConfig`) |
| StatisticsController NPE | task 19, decisions 2026-11-01 |
| 테스트가 실DB(Supabase)에 붙음 (2건: HighlightRepositoryTest·DemoApplicationTests / DemoApplicationTests+members) | task 23, 두 클래스 `@ActiveProfiles("test")` + `application-test.yml`(H2) |
| HighlightWithContent ↔ HighlightResponseDto 불일치 | task 09, `types/index.ts`에 HighlightWithContent 없음 |
| 하이라이트 "영화/드라마" 탭 | task 11, `CATEGORY_FILTER_TABS` 5개 |
| /api/contents/filter 전체 반환 | task 17, `getFilteredContents`가 memberId 필수 |
| 비밀번호 72바이트 | task 14, `MaxUtf8Bytes`, decisions 2026-10-24 |
| 프론트 Bearer 헤더 | task 21, `api.ts send()` + 수동 검증 체크리스트 9번 |

### 부분만 해결된 항목 (열림 유지, 해결된 부분을 줄 끝에 적음)
- `ContentService.getFilteredContents` ANIME / YEAR(): YEAR() 부분은 task 17에서 범위 비교로 해결, ANIME 부분(`ContentService.java:109`)은 그대로 열림.
- 로그인 횟수 제한·이메일 중복·대소문자: 이메일 중복은 task 21-1에서 해결, 횟수 제한(24-2 B2)과 아이디 대소문자 정책은 열림.

### 배포 전 6개 (25 관련)
1. GlobalExceptionHandler catch-all → **25-e 또는 25-f 후보로 표시** (24-2 B6, 요청 사항)
2. actuator exposure 명시 → 25-e
3. 프론트 호출 경로 두 갈래 통일 여부 → 25-d
4. 허용 origin 기본값이 localhost:3000뿐 → 25-d
5. 로그인 횟수 제한 → 24-2 B2 (25 앞/뒤는 사용자 결정)
6. /api/auth/refresh 횟수 제한 → 같이

### 판단 불가 / 실행 필요 (모두 `[ ]` 유지, 줄에 사유를 적음)
- **GlobalExceptionHandler catch-all의 실제 응답**: 코드(`GlobalExceptionHandler.java:94-102`)상 500이 되는 경로가 맞지만 실행해 재현하지 않았다.
- **허용되지 않은 origin의 CORS 403 본문 형식**: 403 상태는 `CorsConfigTest`로 확인되나 본문(ErrorResponse 여부)은 어떤 테스트도 보지 않는다.
- **test 폴더 `DemoApplication`이 실제로 감사(@EnableJpaAuditing 등)를 가리는지**: 파일이 남아 있는 것만 확인했고 영향은 24-2에서도 미실행이다.
- **origin 3001/127.0.0.1에서의 브라우저 동작**: 설정은 코드로 확인했지만 브라우저에서 실행해 보지 않았다.

## 수정 제안 (decisions.md는 바꾸지 않음)
- **45번 (2026-11-01 Testcontainers)**: "postgres:15-alpine으로 가정" → 현재 테스트는 `postgres:17-alpine`(task 20 후반 변경). 문구를 현재 값으로 바꾸거나 "task 20 후반에 17로 변경" 덧붙이기.
- **53번 (2026-11-02 성능 확인)**: "실제 DB 제약 존재는 미확인" → task 22 발견 4에서 "해소(모두 존재)"로 바뀜. 정정 문구 추가 필요.
- **8번 (2026-10-20 Content.userId 1L 유지)**: decisions 32(Member `@ManyToOne` + `member_id` NOT NULL)로 대체됨. "→ 32번으로 대체"를 표시.
- 참고: 26번("Redis 캐싱/비동기는 후순위")도 Redis 캐싱 구현 후 낡았다(24-2 F-3). 이번 요청 범위(45/53/8) 밖이라 제안으로만 적는다.
- "task 24 decisions 후보 미반영"(24-2 F-2): 후보 내용이 현재 파일에 없어 이번에 추가하지 않았다. 그래서 decisions.md는 변경 없음.

## 발견한 문제 (고치지 않고 기록만)
- 24-2의 B3(Kakao 예외 처리), B4(URL 인코딩), B5(Redis timeout)는 25-f 후보이지만 backlog에 항목이 없다. 이번 범위는 기존 항목 정리라 추가하지 않았다. 25-f 때 backlog에 올릴지 결정 필요.
- 기존 항목 "HighlightServiceTest 길이 초과 테스트"와 "HighlightServiceTest.saveHighlight_Fail_TooLongText ..."는 같은 문제의 중복이다. 삭제하지 않고 둘 다 열림으로 두었다(서로 참조 표시).

## 정리 (면접/트러블슈팅용)
1. **문제 상황**: backlog 50개 중 이미 해결된 항목이 모두 `[ ]`라 "무엇이 남았는지"를 알 수 없었고, 배포 전에 처리할 것과 나중으로 미룰 것이 섞여 있었다.
2. **고려한 선택지**: ① 기억/task 요약에 의존해 한 번에 체크 ② 항목마다 현재 코드와 task 번호로 근거를 확인해 판정 ③ 오래된 항목은 삭제하고 새로 작성.
3. **선택 이유**: ②. 시간이 더 들지만 닫은 근거가 줄 끝에 남아 나중에 검증할 수 있다. ①은 task 파일이 "해결"이라 적어도 코드가 실제로 그런지 모르고, ③은 이력이 사라진다. 트레이드오프: 12개만 닫혔고 근거가 약한 항목은 일부러 열어 두어 backlog가 길다.
4. **막혔던 부분과 해결**: 해결 여부가 task 파일과 코드에서 엇갈리는 항목이 있었다(예: task 09는 "브라우저 확인 전이라 backlog 체크 안 함"). 코드를 직접 보고 판정하고, 실행이 필요한 것은 닫지 않고 "판단 불가" 사유를 적었다.

## 복습 질문
(토글 안에 답을 적어주세요. 프론트 질문은 없다.)

<details><summary>Q1. 테스트가 H2로만 돌 때 PostgreSQL 전용 쿼리는 어떤 위험이 있나요? (예: <code>findActiveDays</code>, <code>findMostActiveDay</code>의 EXTRACT/LIMIT) 이 프로젝트는 그 위험을 어떻게 다루고 있고, 이 PC와 CI에서 각각 그 검증이 실제로 도는지 어떻게 확인하나요?</summary>

(여기에 답)

</details>

<details><summary>Q2. 이번 backlog 정리에서 "근거 없이 닫지 않는" 이유는 무엇인가요? 근거 없이 닫으면 나중에 어떤 문제가 생기고, 이번에 "판단 불가"로 남긴 항목은 어떤 종류였나요?</summary>

(여기에 답)

</details>
