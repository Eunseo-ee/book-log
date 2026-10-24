- 2026-10-19: 검색 화면 전에 백엔드에 externalId/isSaved부터 추가 (프론트는 다음 세션)
- 2026-10-19: 이번 범위는 Kakao/TMDB만. AniList는 @Service 주석 처리 상태라 제외
- 2026-10-19: externalId 없는 검색 결과는 저장 차단 유지
- 2026-10-19: isSaved는 캐시에 넣지 않고 요청마다 IN 쿼리로 채운다 (사용자 상태라서)
- 2026-10-19: TMDB authorOrDirector는 "상세 정보 참조" 문자열 대신 null로 내려준다. 화면은 null이면 "-" 표시. 감독 연동(credits API)은 백로그, currentPage 항상 0, isSaved의 userId 미반영(JWT 병합 시), 배지 라벨 표기 통일
- 2026-10-20: 평점은 선택 입력. 보고 싶은 작품(WANT_to_WATCH)은 별점 없이 저장할 수 있어야 하므로, 서버가 rating null을 허용하고 프론트는 비우면 필드를 보내지 않는다
- 2026-10-20: 캘린더 CORS는 검색/저장과 같은 Next 프록시로 통일 (백엔드 전역 CORS 설정은 하지 않음)
- 2026-10-20: Content.userId는 @Builder.Default로 1L 유지 (JWT 병합 시 교체). 기존 user_id NULL 행은 SQL로 직접 보정
- 2026-10-20: 사이드바 미니 캘린더는 오늘 날짜에도 점을 그린다
- 2026-10-21: CORS는 SecurityConfig의 전역 CorsConfigurationSource(/api/**, 허용 origin은 app.cors.allowed-origins, 기본 http://localhost:3000)로만 관리한다. StatisticsController의 @CrossOrigin("*")는 모든 origin을 열고 컨트롤러별로 누락이 생기는 방식이라 제거 (10-20 "백엔드 전역 CORS 설정은 하지 않음"을 대체)
- 2026-10-24: 로그인/JWT는 feat/security를 머지하지 않고 새로 설계한다. Member.id는 Long(IDENTITY): 기존 Content.userId(Long)와 맞고 id가 외부에 노출되지 않아 열거 위험이 낮다. 테이블명은 members("user"는 PostgreSQL 예약어). 비밀번호는 BCrypt 해시로만 저장하고 로그/응답/예외 메시지에 평문을 남기지 않는다 (task 13)
- 2026-10-24: 로그인 실패는 아이디 없음/비밀번호 틀림을 구분하지 않고 같은 예외(InvalidCredentialsException)·같은 상태 코드(401)·같은 메시지로 응답한다. 구분하면 "이 아이디는 존재한다"는 사실이 새어 아이디 열거와 대상 지정 공격이 쉬워진다. 없는 아이디도 더미 해시와 BCrypt 비교를 한 번 해서 응답 시간 차이로도 구분되지 않게 한다 (task 13)
- 2026-10-24: 가입 아이디 중복은 IllegalStateException(400)을 재사용하지 않고 DuplicateUsernameException을 만들어 409로 응답한다. 클라이언트가 "입력 검증 실패(400)"와 "이미 있는 아이디(409)"를 상태 코드로 구분할 수 있고, IllegalStateException은 서비스 내부 상태 오류까지 같이 400으로 삼켜 의미가 흐려지기 때문 (task 13)

# 확정된 설계 결정

- 선택 조건이 있는 조회는 Specification으로 구현한다. 조건이 null이면 null을 반환해 and()가 무시하게 한다.
  (JPQL의 `:p IS NULL OR ...`은 PostgreSQL에서 null 파라미터 타입 추론 오류 위험)
- 연도/월 필터는 YEAR(col) 대신 범위 비교 [시작일, 다음 기간 시작일)로 한다. (인덱스 활용, 경계 검증)
- 연도 기준은 하이라이트 createdAt이 아니라 부모 Content.viewDate.
- '애니' 타입은 ANIME + ANIME_MOVIE + ANIME_TVA를 모두 포함한다. (TMDB 애니가 ANIME_MOVIE/ANIME_TVA로 저장됨)
  category가 null이거나 ALL이면 필터 없음.
- LAZY 연관관계를 목록으로 내릴 때는 @EntityGraph로 N+1을 막는다.
- 입력값 검증은 컨트롤러 @Validated 대신 서비스에서 IllegalArgumentException을 던진다.
  (기존 GlobalExceptionHandler가 400 처리. @Validated는 ConstraintViolationException이 되어 500으로 샐 수 있음)
- Redis 캐싱/비동기는 후순위.