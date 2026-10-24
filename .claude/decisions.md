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
- 2026-10-24: JWT 필터는 요청마다 DB를 조회하지 않고 토큰의 claim(sub=userId, username, role)으로 AuthenticatedMember를 만든다. stateless 유지와 DB 부하 감소가 이유. 트레이드오프: 회원이 삭제되거나 role이 바뀌어도 access 토큰이 만료될 때까지는 이전 정보로 인증된다. 그래서 access 만료는 짧게 두고, 회원 존재 확인이 꼭 필요한 곳(refresh)만 DB를 조회한다 (task 14)
- 2026-10-24: access/refresh는 같은 키로 서명하되 type claim으로 구분한다. 필터는 access만, /api/auth/refresh는 refresh만 인정한다. type이 없으면 refresh 토큰을 access처럼 쓸 수 있다 (task 12 발견 6번) (task 14)
- 2026-10-24: JwtAuthenticationFilter는 토큰이 없거나 잘못돼도 예외 없이 인증 없이 통과시킨다. 지금은 permitAll이라 막을 대상이 없고, 인증 강제는 SecurityConfig의 authenticated가 맡는다(task 19). 필터는 빈이 아니라 SecurityConfig에서 new로 만들어 체인에만 넣는다 (@Component면 서블릿 필터로 자동 등록되어 체인 밖에서 한 번 더 실행될 수 있음) (task 14)
- 2026-10-24: refresh는 새 access 토큰만 발급하고 refresh 토큰은 회전하지 않는다. 서버가 refresh를 저장하지 않아(stateless) 구현이 단순하지만, 로그아웃이나 탈취된 refresh 폐기가 불가능하고 만료 전까지 계속 유효하다. 재발급 시에는 회원 존재 여부만 DB로 확인한다. 저장소(DB/Redis) 기반 회전·폐기는 백로그 (task 14)
- 2026-10-24: 가입 비밀번호는 문자 수(8~64)에 더해 UTF-8 72바이트 이하여야 한다. Spring Security 6.4.2의 BCrypt는 72바이트 초과분을 예외 없이 잘라 해시하므로, 한글 비밀번호의 뒷부분이 조용히 무시되는 것을 입력 단계에서 막는다. 로그인은 긴 입력이 와도 500 없이 기존과 같은 401이라 추가 조치가 필요 없었다 (task 14)
