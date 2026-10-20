# 인박스 (원인/우선순위 몰라도 일단 적기)

- 2026-10-20 | 검색 화면 | 다른 화면 갔다 와도 검색창에 이전 검색어가 남아 있음
- 2026-10-20 | 저장 폼 | 저장 성공 후 어디서 볼 수 있는지 안내 없음
- 2026-10-20 | 캘린더 | 저장한 것들이 들어와있긴 한데 클릭 안됨
  - 분류: 미구현. 날짜 칸에 onClick/Link/상세 화면/날짜별 조회 API가 없음(CalendarView.tsx:124-131). "들어와 있음"은 CORS 허용된 통계 리캡 카드일 가능성(미확인). 상세: tasks/04-calendar-investigation.md
- 2026-10-20 | 캘린더 | 내가 저장한 날짜에 캘린더에 아무것도 표시 안됨 사이드바 캘린더도
  - 분류: 버그(원인 3개 겹침). A CORS 코드상 성립(캘린더는 8080 직접 호출 + CalendarController CORS 없음 → MVC가 preflight 403). B userId null 코드상 성립(@Builder.Default 없음, DB 값 미확인). C1 사이드바는 오늘 날짜 점을 숨김(MiniCalendar.tsx:87). 상세: tasks/04-calendar-investigation.md