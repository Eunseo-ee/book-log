# 코드 기능 설명서

> 이 프로젝트에서 구현한 모든 기능을 코드 흐름 순서대로 설명.
> "이 코드가 왜 있고, 어떻게 동작하는가"에 초점.

---

## 목차

1. [프로젝트 전체 흐름 한눈에 보기](#1-프로젝트-전체-흐름-한눈에-보기)
2. [공통 기반 코드](#2-공통-기반-코드)
3. [인증 시스템 (JWT + Spring Security)](#3-인증-시스템-jwt--spring-security)
4. [콘텐츠 관리](#4-콘텐츠-관리)
5. [하이라이트 (명대사/문장 저장)](#5-하이라이트-명대사문장-저장)
6. [통합 검색](#6-통합-검색)
7. [캘린더](#7-캘린더)
8. [통계](#8-통계)
9. [예외 처리](#9-예외-처리)

---

## 1. 프로젝트 전체 흐름 한눈에 보기

이 앱은 **책·영화·드라마·애니메이션을 기록하는 서비스**다.
핵심 흐름은 아래와 같다.

```
사용자
  │
  ├── 1. 회원가입/로그인       → JWT 토큰 발급
  │
  ├── 2. 콘텐츠 검색           → Kakao/TMDB API 호출 → 결과 반환
  │         (책/영화/드라마)
  │
  ├── 3. 콘텐츠 저장           → DB에 기록 저장
  │         (별점, 한줄평, 관람일)
  │
  ├── 4. 하이라이트 저장       → 인상 깊은 문장/장면 저장
  │
  ├── 5. 캘린더 조회           → 이번 달에 기록한 날짜 목록
  │
  └── 6. 통계 조회             → 이번 달 카테고리별/장르별 통계
```

**API 목록:**
| 메서드 | URL | 설명 |
|---|---|---|
| POST | /api/auth/register | 회원가입 |
| POST | /api/auth/login | 로그인 → 토큰 발급 |
| GET | /api/search?category=BOOK&query=해리포터 | 통합 검색 |
| POST | /api/contents | 콘텐츠 저장 |
| GET | /api/contents/filter?year=2026&category=BOOK | 필터 조회 |
| GET | /api/contents/check?externalId=123&category=BOOK | 저장 여부 확인 |
| POST | /api/highlights | 하이라이트 저장 |
| PUT | /api/highlights/{id} | 하이라이트 수정 |
| DELETE | /api/highlights/{id} | 하이라이트 삭제 |
| GET | /api/calendar/activities?year=2026&month=5 | 월별 활동 날짜 |
| GET | /api/statistics?year=2026&month=5 | 월별 통계 |

---

## 2. 공통 기반 코드

### BaseTimeEntity — 생성/수정 시간 자동 기록

```java
// domain/BaseTimeEntity.java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime modifiedAt;
}
```

**왜 필요한가?**
`Content`, `Highlight`, `Member` 모두 언제 만들어지고 수정됐는지 알아야 한다.
그런데 모든 엔티티마다 같은 필드를 복붙하면 중복이다.
그래서 `BaseTimeEntity`를 만들고 나머지가 `extends`로 상속받는다.

**동작 방식:**
- `@MappedSuperclass` → 이 클래스 자체는 테이블이 안 만들어지고, 상속받는 클래스의 테이블에 컬럼이 추가됨
- `@EntityListeners(AuditingEntityListener.class)` → JPA가 저장/수정 시 자동으로 시간을 넣어줌
- `@CreatedDate` → 처음 저장될 때만 입력, `updatable = false`로 이후 변경 불가
- `@LastModifiedDate` → 수정될 때마다 자동 갱신
- `DemoApplication.java`에 `@EnableJpaAuditing`이 있어야 이 기능이 활성화됨

---

### Category, Status — 타입 분류용 Enum

```java
// domain/Category.java
public enum Category {
    BOOK, MOVIE, TV, ANIME, ANIME_MOVIE, ANIME_TVA, ALL
}

// domain/Status.java
public enum Status {
    READING, WATCHING, COMPLETED, WANT_to_WATCH, STOPPED
}
```

**왜 Enum인가?**
"도서", "영화" 같은 값을 String으로 저장하면 "Book", "book", "BOOK" 등
오타/대소문자 차이로 버그가 생긴다.
Enum은 미리 정해진 값만 사용할 수 있어서 안전하다.

`@Enumerated(EnumType.STRING)`으로 DB에 `"BOOK"`, `"MOVIE"` 문자열로 저장된다.
(숫자로 저장하면 나중에 enum 순서를 바꿀 때 데이터가 망가진다.)

---

### ApiConfig — 외부 API 키 관리

```java
// config/ApiConfig.java
@Configuration
@ConfigurationProperties(prefix = "api")
@Getter @Setter
public class ApiConfig {
    private String kakaoKey;   // application-api-key.yml의 api.kakao-key
    private String tmdbToken;  // application-api-key.yml의 api.tmdb-token

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(3))
                .build();
    }
}
```

**`@ConfigurationProperties`가 하는 일:**
`application-api-key.yml`에서 `api.kakao-key`, `api.tmdb-token` 값을 읽어서
자동으로 `kakaoKey`, `tmdbToken` 필드에 주입한다.
`DemoApplication`에 `@EnableConfigurationProperties(ApiConfig.class)`가 있어서 활성화됨.

**`RestTemplate`이란?**
외부 API(Kakao, TMDB)를 HTTP로 호출할 때 쓰는 Spring 내장 HTTP 클라이언트.
`connectTimeout(3초)` → 서버에 연결되지 않으면 3초 후 포기
`readTimeout(3초)` → 응답이 3초 안에 안 오면 포기
→ 외부 API가 느릴 때 우리 서버가 함께 멈추는 것을 방지

---

## 3. 인증 시스템 (JWT + Spring Security)

> 로그인한 사람만 API를 쓸 수 있도록 막고, 누가 요청하는지 식별하는 시스템.

### 전체 인증 흐름

```
[회원가입]
사용자 → POST /api/auth/register → AuthController
           → AuthService.register()
           → BCrypt 암호화
           → MemberRepository.save()  → DB 저장

[로그인]
사용자 → POST /api/auth/login → AuthController
           → AuthService.login()
           → DB에서 username 조회
           → BCrypt.matches() 비밀번호 비교
           → JwtTokenProvider.generateAccessToken() + generateRefreshToken()
           → { accessToken, refreshToken } 반환

[인증이 필요한 API 호출]
사용자 → Authorization: Bearer {accessToken} 헤더 포함해서 요청
           → JwtAuthenticationFilter 실행
           → JwtTokenProvider.validateToken() 서명 검증
           → JwtTokenProvider.getAuthentication() 사용자 정보 복원
           → SecurityContextHolder에 저장
           → 컨트롤러 실행
```

---

### Member — 회원 엔티티

```java
// domain/Member.java
@Entity
@Table(name = "members")
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;               // 기본키: UUID (auto_increment 대신)

    @Column(unique = true)
    private String username;       // 로그인 아이디 (중복 불가)

    private String password;       // BCrypt 암호화된 비밀번호
    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;             // ROLE_USER / ROLE_ADMIN
}
```

**UUID vs Long(auto_increment):**
- `Long`은 DB에서 순서대로 1, 2, 3... 번호를 매김 → 서버가 여러 개면 충돌 위험
- `UUID`는 애플리케이션이 직접 고유한 값을 생성 → DB에 의존하지 않아 분산 환경에 안전
- PostgreSQL은 `uuid` 타입을 네이티브로 지원해서 효율적

---

### MemberRepository — 회원 데이터 조회

```java
// repository/MemberRepository.java
public interface MemberRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByUsername(String username);
    boolean existsByUsername(String username);
}
```

**Spring Data JPA 네이밍 규칙:**
`findBy필드명(값)` 형태로 메서드를 선언하면 Spring이 자동으로 SQL을 만들어준다.
- `findByUsername("hong")` → `SELECT * FROM members WHERE username = 'hong'`
- `existsByUsername("hong")` → `SELECT COUNT(*) > 0 FROM members WHERE username = 'hong'`

**Optional이란?**
조회 결과가 없을 때 `null` 대신 `Optional.empty()`를 반환해서
`NullPointerException`을 방지한다.
`.orElseThrow(() -> new Exception("없음"))` 으로 없을 때 예외를 던질 수 있다.

---

### AuthService — 회원가입과 로그인 로직

```java
// service/AuthService.java

// 회원가입
@Transactional
public void register(SignupRequestDto dto) {
    // 1. username 중복 체크
    if (memberRepository.existsByUsername(dto.getUsername())) {
        throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
    }

    // 2. 비밀번호 BCrypt 암호화 후 저장
    Member member = Member.builder()
            .username(dto.getUsername())
            .password(passwordEncoder.encode(dto.getPassword()))  // 암호화
            .email(dto.getEmail())
            .role(Role.ROLE_USER)
            .build();

    memberRepository.save(member);
}

// 로그인
@Transactional(readOnly = true)
public TokenResponseDto login(LoginRequestDto dto) {
    // 1. DB에서 회원 찾기
    Member member = memberRepository.findByUsername(dto.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

    // 2. 비밀번호 검증 (BCrypt는 매번 다르게 암호화되므로 equals() 불가, matches() 사용)
    if (!passwordEncoder.matches(dto.getPassword(), member.getPassword())) {
        throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
    }

    // 3. 토큰 발급
    String accessToken = jwtTokenProvider.generateAccessToken(member.getUsername());
    String refreshToken = jwtTokenProvider.generateRefreshToken(member.getUsername());
    return new TokenResponseDto(accessToken, refreshToken);
}
```

**BCrypt란?**
단방향 암호화 함수. 한 번 암호화하면 복호화(원래 값으로 되돌리기)가 불가능하다.
그래서 로그인 시 검증은 `matches(입력한평문, 저장된암호화값)`으로 한다.
`encode("1234")` 를 두 번 실행하면 매번 다른 값이 나온다 (내부에서 랜덤 salt 사용).
→ `"1234".equals(encodedPassword)` 방식은 절대 동작하지 않는다.

---

### JwtTokenProvider — 토큰 생성/검증

```java
// security/JwtTokenProvider.java

// 생성자: yml의 jwt.secret을 BASE64 디코딩해서 서명 키 생성
public JwtTokenProvider(@Value("${jwt.secret}") String secret, ...) {
    this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    ...
}

// accessToken 생성 (15분)
public String generateAccessToken(String username) {
    return buildToken(username, accessTokenExpiration);
}

// refreshToken 생성 (7일)
public String generateRefreshToken(String username) {
    return buildToken(username, refreshTokenExpiration);
}

// 내부 공통 생성 로직
private String buildToken(String username, long expiration) {
    Date now = new Date();
    return Jwts.builder()
            .subject(username)          // payload에 username 저장
            .issuedAt(now)              // 발급 시각
            .expiration(new Date(now.getTime() + expiration))  // 만료 시각
            .signWith(secretKey)        // 서명 (위변조 감지용)
            .compact();
}

// 토큰 검증 (만료/변조/형식 오류 체크)
public boolean validateToken(String token) {
    try {
        parseClaims(token);  // 파싱 성공 = 유효
        return true;
    } catch (ExpiredJwtException e) { ... }   // 만료
    catch (MalformedJwtException e) { ... }   // 형식 오류
    return false;
}

// 토큰 → Spring Security의 Authentication 객체 변환
public Authentication getAuthentication(String token) {
    String username = getUsername(token);
    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
    return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
}
```

**JWT 구조:**
JWT는 `.`으로 구분된 3부분으로 이루어진다.
```
eyJhbGci...  .  eyJzdWIi...  .  SflKxw...
   Header        Payload        Signature
 (알고리즘)    (username, 만료) (서명 - 위변조 감지)
```
서버는 Signature를 secretKey로 검증한다. 누군가 payload를 수정하면 Signature가 맞지 않아 거부된다.

---

### JwtAuthenticationFilter — 모든 요청에서 토큰 검사

```java
// security/JwtAuthenticationFilter.java
// OncePerRequestFilter → 요청당 정확히 1번 실행

@Override
protected void doFilterInternal(HttpServletRequest request, ...) {

    // 1. "Authorization: Bearer eyJhb..." 헤더에서 토큰 꺼내기
    String token = resolveToken(request);

    // 2. 토큰이 있고 유효하면 인증 처리
    if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
        Authentication auth = jwtTokenProvider.getAuthentication(token);
        SecurityContextHolder.getContext().setAuthentication(auth);
        // 이 줄 이후 컨트롤러에서 "누가 요청했는지" 알 수 있음
    }

    // 3. 다음 필터로 넘기기 (토큰 없어도 계속 진행, 접근 제어는 SecurityConfig에서)
    filterChain.doFilter(request, response);
}

private String resolveToken(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if (bearer != null && bearer.startsWith("Bearer ")) {
        return bearer.substring(7);  // "Bearer " 7글자 잘라내고 토큰만 반환
    }
    return null;
}
```

**SecurityContextHolder란?**
현재 요청을 처리하는 스레드에서 "지금 로그인한 사람이 누구인가"를 보관하는 저장소.
여기에 저장하면 해당 요청이 끝날 때까지 어디서든 꺼내 쓸 수 있다.
(나중에 `@AuthenticationPrincipal`로 현재 사용자 꺼내는 것도 여기서 가져오는 것)

---

### CustomUserDetailsService — username으로 사용자 로드

```java
// security/CustomUserDetailsService.java
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자 없음: " + username));

        return new User(
                member.getUsername(),
                member.getPassword(),
                List.of(new SimpleGrantedAuthority(member.getRole().name()))
                // ROLE_USER, ROLE_ADMIN 권한 정보
        );
    }
}
```

**왜 필요한가?**
Spring Security는 인증 시 `UserDetailsService.loadUserByUsername()`을 호출해서
사용자 정보를 가져오도록 설계되어 있다.
이 인터페이스를 구현(`implements`)해서 "우리 DB에서 가져오도록" 커스텀한 것이다.

---

### SecurityConfig — Spring Security 전체 설정

```java
// config/SecurityConfig.java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 1. BCrypt 암호화 도구를 Spring 빈으로 등록
    //    → AuthService에서 @RequiredArgsConstructor로 자동 주입받아 사용
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 2. CSRF 비활성화: REST API는 JWT로 인증하므로 CSRF 토큰 불필요
            .csrf(AbstractHttpConfigurer::disable)

            // 3. 세션 비활성화: JWT 기반 → 서버가 세션을 저장하지 않음
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 4. URL별 인증 요구 설정
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()  // 나머지는 JWT 토큰 필요
            )

            // 5. JWT 필터를 Spring Security 기본 필터 앞에 등록
            //    → 요청이 들어오면 JWT부터 확인하고 그 다음 Spring Security 처리
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
```

**필터 체인이란?**
HTTP 요청이 컨트롤러에 도달하기 전에 거쳐야 하는 관문들의 연결.
```
요청 → JwtAuthenticationFilter → (다른 Spring Security 필터들) → 컨트롤러
```
`addFilterBefore(A, B)` → B 필터 앞에 A를 끼워 넣는다는 뜻.

---

## 4. 콘텐츠 관리

> 책/영화/드라마/애니 기록을 저장하고 조회하는 핵심 기능.

### Content — 콘텐츠 엔티티

```java
// domain/Content.java
@Entity
public class Content extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String externalId;        // Kakao/TMDB에서 받아온 원본 ID
    private String title;             // 제목
    private String authorOrDirector;  // 저자 또는 감독
    private String thumbnailUrl;      // 표지/포스터 이미지 URL

    @Enumerated(EnumType.STRING)
    private Category category;        // BOOK, MOVIE, TV, ANIME...

    @Enumerated(EnumType.STRING)
    private Status status;            // WATCHING, COMPLETED...

    private Double rating;            // 별점 (0.0 ~ 5.0)
    private LocalDate viewDate;       // 관람/독서 날짜
    private String comment;           // 한줄평

    @Column(name = "user_id")
    private Long userId = 1L;         // ⚠ 임시 고정값, 인증 완성 후 실제 userId로 교체 예정

    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Highlight> highlights = new ArrayList<>();
}
```

**`@OneToMany`의 의미:**
"Content 1개에 Highlight가 여러 개 달릴 수 있다"는 관계 설정.
- `cascade = CascadeType.ALL` → Content를 삭제하면 달린 Highlight도 전부 삭제
- `orphanRemoval = true` → highlights 리스트에서 항목을 제거하면 DB에서도 삭제
- `mappedBy = "content"` → 연관관계의 주인은 Highlight.content 필드임을 선언

---

### ContentService — 콘텐츠 비즈니스 로직

```java
// service/ContentService.java

// 저장
@Transactional
public Long saveContent(ContentRequestDto requestDto) {
    // 1. 평점 범위 검증 (0~5)
    if (requestDto.getRating() < 0 || requestDto.getRating() > 5.0) {
        throw new IllegalArgumentException("평점은 0에서 5 사이여야 합니다.");
    }

    // 2. 미래 날짜 방지 (오늘 이후 날짜로 기록 불가)
    if (requestDto.getViewDate().isAfter(LocalDate.now())) {
        throw new IllegalArgumentException("미래 날짜의 기록은 저장할 수 없습니다.");
    }

    // 3. 중복 저장 방지 (같은 externalId + 같은 category는 1개만)
    if (isAlreadySaved(requestDto.getExternalId(), requestDto.getCategory())) {
        throw new IllegalStateException("이미 저장된 콘텐츠입니다.");
    }

    // 4. DTO → 엔티티 변환 후 저장, 생성된 ID 반환
    return contentRepository.save(requestDto.toEntity()).getId();
}

// 필터 조회
public List<ContentResponseDto> getFilteredContents(Integer year, Category category) {
    List<Content> contents;

    if (year != null && category != null) {
        contents = contentRepository.findByYearAndCategory(year, category);
    } else if (year != null) {
        contents = contentRepository.findByYear(year);
    } else if (category != null) {
        contents = contentRepository.findByCategory(category);
    } else {
        contents = contentRepository.findAll();  // 조건 없으면 전체 조회
    }

    return contents.stream()
            .map(ContentResponseDto::new)   // 엔티티 → DTO 변환
            .collect(Collectors.toList());
}
```

**DTO와 Entity를 왜 분리하는가?**
- Entity는 DB 테이블과 1:1 대응. 모든 필드가 있음.
- DTO(Data Transfer Object)는 API 요청/응답에 필요한 필드만 담음.
- Entity를 직접 반환하면 불필요한 필드 노출, 순환 참조 문제 등이 생긴다.
- `ContentRequestDto.toEntity()` → 요청 DTO를 Entity로 변환
- `new ContentResponseDto(entity)` → Entity를 응답 DTO로 변환

---

### ContentController — HTTP 요청 처리

```java
// controller/ContentController.java

// 저장: POST /api/contents
@PostMapping
public ResponseEntity<Long> saveContent(@Valid @RequestBody ContentRequestDto requestDto) {
    Long savedId = contentService.saveContent(requestDto);
    // 201 Created + Location 헤더에 저장된 리소스 경로 포함 (REST 관례)
    return ResponseEntity.created(URI.create("/api/contents/" + savedId)).body(savedId);
}

// 중복 확인: GET /api/contents/check?externalId=123&category=BOOK
@GetMapping("/check")
public ResponseEntity<Boolean> checkDuplicate(
        @RequestParam String externalId,
        @RequestParam Category category) {
    boolean isSaved = contentService.isAlreadySaved(externalId, category);
    return ResponseEntity.ok(isSaved);  // true 또는 false 반환
}

// 필터 조회: GET /api/contents/filter?year=2026&category=BOOK
@GetMapping("/filter")
public ResponseEntity<List<ContentResponseDto>> getFilteredContents(
        @RequestParam(required = false) Integer year,      // 없어도 됨
        @RequestParam(required = false) Category category) {
    return ResponseEntity.ok(contentService.getFilteredContents(year, category));
}
```

**`@Valid`가 하는 일:**
요청 DTO의 `@NotBlank`, `@Min`, `@Max` 같은 검증 애노테이션을 실행한다.
검증 실패 시 `MethodArgumentNotValidException`이 발생하고
`GlobalExceptionHandler`가 잡아서 400 에러로 응답한다.

---

## 5. 하이라이트 (명대사/문장 저장)

> 콘텐츠에 달린 인상 깊은 문장이나 장면을 저장하는 기능.

### Highlight — 하이라이트 엔티티

```java
// domain/Highlight.java
@Entity
public class Highlight extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String text;       // 명대사/문장 내용 (긴 텍스트 허용)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id")
    private Content content;   // 어떤 콘텐츠에 달린 하이라이트인지 (FK)

    private Integer page;      // 도서: 페이지 번호
    private Integer season;    // 드라마/애니: 시즌
    private Integer episode;   // 드라마/애니: 화수
    private String timestamp;  // 영상: 타임스탬프 (예: "01:23:45")
}
```

**`@ManyToOne(fetch = FetchType.LAZY)`의 의미:**
- "여러 Highlight가 하나의 Content에 속한다"는 관계
- `FetchType.LAZY` → Highlight를 조회할 때 Content를 **즉시 조회하지 않음**
- Content가 필요한 순간에만 DB를 조회 (성능 최적화)
- 반대인 `EAGER`는 항상 함께 조회 (불필요한 쿼리 발생 위험)

---

### HighlightService — 하이라이트 CRUD

```java
// service/HighlightService.java

// 저장
@Transactional
public Long saveHighlight(HighlightRequestDto requestDto) {
    // 1. 연결할 콘텐츠가 DB에 실제로 있는지 확인
    Content content = contentRepository.findById(requestDto.getContentId())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));

    // 2. 하이라이트 엔티티 생성 (빌더 패턴)
    Highlight highlight = Highlight.builder()
            .text(requestDto.getText())
            .page(requestDto.getPage())
            .season(requestDto.getSeason())
            .episode(requestDto.getEpisode())
            .timestamp(requestDto.getTimestamp())
            .content(content)   // 외래키 연결
            .build();

    return highlightRepository.save(highlight).getId();
}

// 수정 (Dirty Checking)
@Transactional
public Long updateHighlight(Long id, HighlightRequestDto requestDto) {
    Highlight highlight = highlightRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("해당 하이라이트가 없습니다."));

    highlight.update(requestDto.getText(), ...);  // 엔티티 값 변경
    // ← save() 호출 없음! @Transactional 안에서 엔티티 값을 바꾸면
    //   트랜잭션 종료 시 JPA가 자동으로 UPDATE 쿼리 실행 (Dirty Checking)
    return id;
}

// 삭제
@Transactional
public void deleteHighlight(Long id) {
    highlightRepository.deleteById(id);
}
```

**Dirty Checking(변경 감지)이란?**
JPA는 `@Transactional` 안에서 조회한 엔티티의 변경을 추적한다.
트랜잭션이 끝날 때 처음 조회한 값과 비교해서 변경된 필드가 있으면 자동으로 UPDATE를 날린다.
그래서 `save(highlight)`를 명시적으로 호출하지 않아도 수정이 반영된다.

---

## 6. 통합 검색

> 책은 Kakao API, 영화/드라마/애니는 TMDB API를 통해 검색하는 기능.
> `SearchProvider` 인터페이스로 각 API 구현체를 교체 가능하게 설계.

### SearchProvider — 검색 구현체 공통 규격

```java
// service/SearchProvider.java
public interface SearchProvider {
    boolean supports(Category category);  // 이 서비스가 해당 카테고리를 처리할 수 있는가?
    List<UnifiedSearchResponse> search(Category category, String query);  // 실제 검색
}
```

**인터페이스를 쓰는 이유:**
Kakao, TMDB, AniList는 API 호출 방식이 전혀 다르다.
하지만 `SearchService`는 이 차이를 모르고, 그냥 "검색 결과를 줘"라고만 요청한다.
인터페이스가 이 다양한 구현체를 하나의 방식으로 사용할 수 있게 해준다.
→ 나중에 새 검색 API를 추가해도 `SearchService` 코드를 수정할 필요가 없다.

---

### SearchService — 요청을 적절한 서비스로 분배

```java
// service/SearchService.java
@Service
public class SearchService {

    // Spring이 SearchProvider를 구현한 모든 클래스를 자동으로 이 리스트에 넣어줌
    // → KakaoSearchService, TmdbSearchService가 자동으로 들어옴
    private final List<SearchProvider> providers;

    public List<UnifiedSearchResponse> search(Category category, String query) {
        if (category == Category.ALL) {
            return searchAll(query);  // 전체 검색: 모든 Provider 호출
        }

        if (category == Category.ANIME) {
            // 애니메이션: TMDB에서 장르 ID 16(Animation)으로 필터링
            return providers.stream()
                    .filter(p -> p.supports(Category.MOVIE) || p.supports(Category.TV))
                    .flatMap(p -> p.search(category, query).stream())
                    .filter(r -> r.getCategory() == Category.ANIME_MOVIE || r.getCategory() == Category.ANIME_TVA)
                    .collect(Collectors.toList());
        }

        // BOOK → KakaoSearchService, MOVIE/TV → TmdbSearchService
        return providers.stream()
                .filter(p -> p.supports(category))   // 담당 서비스 찾기
                .findFirst()
                .map(p -> p.search(category, query)) // 검색 실행
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 카테고리: " + category));
    }
}
```

**`Stream.flatMap()`이란?**
`map()`은 각 항목을 변환한 결과가 리스트면 `[[결과1], [결과2]]` 이중 리스트가 된다.
`flatMap()`은 이것을 `[결과1, 결과2]` 단일 리스트로 평탄화한다.
여러 Provider의 검색 결과를 하나의 리스트로 합칠 때 사용.

---

### KakaoSearchService — 도서 검색 (Kakao API)

```java
// service/KakaoSearchService.java
@Service
public class KakaoSearchService implements SearchProvider {

    @Override
    public boolean supports(Category category) {
        return category == Category.BOOK || category == Category.ALL;
    }

    @Override
    public List<UnifiedSearchResponse> search(Category category, String query) {
        // 1. 인증 헤더 세팅
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + apiConfig.getKakaoKey());

        // 2. Kakao 도서 검색 API 호출
        String url = "https://dapi.kakao.com/v3/search/book?query=" + query;
        ResponseEntity<KakaoBookResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), KakaoBookResponse.class);

        // 3. Kakao 응답 → 우리 통합 규격(UnifiedSearchResponse)으로 변환
        return mapToResponse(response.getBody(), category);
    }
}
```

**`restTemplate.exchange()`가 하는 일:**
HTTP 요청을 보내고 응답을 받아온다.
- 1번째 인수: URL
- 2번째 인수: HTTP 메서드 (GET, POST...)
- 3번째 인수: 요청 헤더/바디
- 4번째 인수: 응답을 어떤 클래스로 파싱할지 → `KakaoBookResponse.class`로 JSON 자동 변환

---

### TmdbSearchService — 영화/드라마/애니 검색 (TMDB API)

```java
// service/TmdbSearchService.java
@Service
public class TmdbSearchService implements SearchProvider {

    @Override
    public List<UnifiedSearchResponse> search(Category category, String query) {
        // multi 검색: 영화(movie)와 드라마(tv)를 한 번에 검색
        String url = "https://api.themoviedb.org/3/search/multi?query=" + query + "&language=ko-KR";

        try {
            ResponseEntity<TmdbResponse> response = restTemplate.exchange(...);
            return mapToResponse(response.getBody(), category);
        } catch (Exception e) {
            // API 장애 시 빈 리스트 반환 (서버 에러 전파 방지)
            log.error("TMDB API 호출 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<UnifiedSearchResponse> mapToResponse(TmdbResponse body, Category category) {
        return body.getResults().stream()
            .filter(item -> "movie".equals(item.getMediaType()) || "tv".equals(item.getMediaType()))
            .map(item -> {
                boolean isMovie = "movie".equals(item.getMediaType());
                // 장르 ID 16 = Animation (TMDB 기준)
                boolean isAnimation = item.getGenreIds() != null && item.getGenreIds().contains(16);

                // 애니메이션 여부 + 영화/TV 여부로 카테고리 결정
                Category itemCategory = isAnimation
                        ? (isMovie ? Category.ANIME_MOVIE : Category.ANIME_TVA)
                        : (isMovie ? Category.MOVIE : Category.TV);

                return UnifiedSearchResponse.builder()
                        .title(isMovie ? item.getTitle() : item.getName())
                        .thumbnailUrl("https://image.tmdb.org/t/p/w500" + item.getPosterPath())
                        .category(itemCategory)
                        ...
                        .build();
            }).collect(Collectors.toList());
    }
}
```

**애니메이션 분류 로직:**
TMDB에는 별도 '애니메이션' 카테고리가 없다. 대신 장르 ID 16이 Animation이다.
그래서 영화인데 장르 16이면 `ANIME_MOVIE`, TV인데 장르 16이면 `ANIME_TVA`로 분류한다.

---

### AnimeSearchService — AniList API (현재 비활성)

```java
// service/AnimeSearchService.java
// ⚠ @Service 애노테이션이 주석 처리됨 → Spring 빈으로 등록 안 됨 → 현재 미사용
// @Service

public class AnimeSearchService implements SearchProvider {
    // AniList GraphQL API 사용
    // REST가 아닌 GraphQL 방식 → POST로 쿼리 문자열을 보냄
    @Override
    public List<UnifiedSearchResponse> search(Category category, String query) {
        String graphqlQuery = """
            query ($search: String) {
              Page(perPage: 10) {
                media(search: $search, type: ANIME) { ... }
              }
            }
            """;
        // GraphQL은 POST + JSON 바디로 쿼리 전송
        Map<String, Object> requestBody = Map.of("query", graphqlQuery, "variables", Map.of("search", query));
        ...
    }
}
```

**왜 비활성화되어 있는가?**
TMDB가 이미 애니메이션을 커버하므로 중복.
AniList는 별도 활성화 시 TMDB 결과에 추가로 겹쳐서 나올 수 있어 임시 비활성 처리.

---

## 7. 캘린더

> 특정 연월에 콘텐츠를 기록한 날짜 목록을 반환.
> 사이드바 캘린더에서 "이 날에 기록했음" 표시(점)를 위한 API.

### CalendarService

```java
// service/CalendarService.java
@Transactional(readOnly = true)
public CalendarResponse getMonthlyActivity(Long userId, int year, int month) {

    // 1. DB에서 해당 월에 기록이 있는 날짜(일)만 조회
    //    → [3, 7, 15, 22] 같은 숫자 리스트
    List<Integer> activeDays = contentRepository.findActiveDays(userId, year, month);

    // 2. 숫자 → DayActivity 객체로 변환
    List<CalendarResponse.DayActivity> dayActivities = activeDays.stream()
            .map(day -> new CalendarResponse.DayActivity(day, null, null))
            .toList();

    return new CalendarResponse(year, month, dayActivities);
}
```

**`ContentRepository.findActiveDays()` 쿼리:**
```sql
SELECT DISTINCT CAST(EXTRACT(DAY FROM view_date) AS INTEGER)
FROM content
WHERE user_id = :userId
  AND EXTRACT(YEAR FROM view_date) = :year
  AND EXTRACT(MONTH FROM view_date) = :month
ORDER BY 1
```
- `EXTRACT(DAY FROM view_date)` → 날짜에서 '일(day)' 숫자만 추출
- `DISTINCT` → 같은 날에 여러 기록이 있어도 날짜 하나만 반환

---

### CalendarResponse — Java Record 사용

```java
// dto/response/CalendarResponse.java
public record CalendarResponse(int year, int month, List<DayActivity> days) {

    public record DayActivity(int day, String thumbnail, Long count) {
        public static DayActivity of(int day) {
            return new DayActivity(day, null, null);
        }
    }
}
```

**`record`란?**
Java 16+에서 추가된 불변 데이터 클래스 문법.
`@Getter`, `@AllArgsConstructor`, `equals()`, `hashCode()`, `toString()`을 자동 생성.
단순히 데이터를 담는 DTO에 적합하고 코드가 훨씬 간결해진다.

---

## 8. 통계

> 특정 월의 카테고리별/장르별 기록 수, 평균 별점, 가장 활발한 요일 등을 계산.

### StatisticsService — 통계 계산

```java
// service/StatisticsService.java
@Transactional(readOnly = true)
public StatisticsResponse getMonthlyStatistics(int year, int month) {

    String currentMonth = String.format("%04d-%02d", year, month);  // "2026-05"
    String previousMonth = ...;  // 전월 계산

    // 1. 카테고리별/장르별 집계 (Interface Projection 사용)
    List<CategoryStatInterface> categoryResults = contentRepository.countByCategoryInterface(currentMonth);
    List<GenreStatInterface> genreResults = contentRepository.countByGenreInterface(currentMonth);

    // 2. Interface Projection → DTO 변환
    List<CategoryStat> categoryStats = categoryResults.stream()
            .map(r -> new CategoryStat(r.getCategory(), r.getCount()))
            .toList();

    // 3. 평균 별점 (소수 첫째 자리 반올림)
    Double rawAvg = contentRepository.getAverageRating(currentMonth);
    double averageRating = rawAvg != null ? Math.round(rawAvg * 10) / 10.0 : 0.0;

    // 4. 전월 대비 증가율 계산
    long current = contentRepository.countByMonth(currentMonth);
    long previous = contentRepository.countByMonth(previousMonth);
    double growthRate = previous == 0 ? (current > 0 ? 100.0 : 0.0) : (double)(current - previous) / previous * 100;

    // 5. 가장 활발한 요일 (PostgreSQL DOW: 0=일, 1=월, ..., 6=토)
    Object[] dayResult = contentRepository.findMostActiveDay(currentMonth);
    String mostActiveDay = convertDowToKorean(((Number) ((Object[]) dayResult[0])[0]).intValue());

    // 6. 취향 분석 메시지 생성
    String tasteAnalysis = generateTasteAnalysis(topCategory, categoryStats);

    return StatisticsResponse.builder()...build();
}
```

**Interface Projection이란?**
JPA에서 GROUP BY 집계 결과를 받을 때 쓰는 기법.
엔티티 대신 인터페이스로 결과를 받는다:
```java
// ContentRepository 안에 선언된 내부 인터페이스
public interface CategoryStatInterface {
    String getCategory();
    Long getCount();
}
```
쿼리 결과의 컬럼명과 인터페이스의 getter 이름을 맞춰주면 Spring이 자동으로 매핑.
`getCategory()` → SELECT 결과의 `category` 컬럼값
`getCount()` → SELECT 결과의 `count` 컬럼값

---

## 9. 예외 처리

### GlobalExceptionHandler — 전역 에러 핸들러

```java
// exception/GlobalExceptionHandler.java
@RestControllerAdvice  // 모든 컨트롤러에서 발생하는 예외를 여기서 잡는다
public class GlobalExceptionHandler {

    // IllegalStateException → 400 Bad Request (중복 저장 등)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException e) {
        return new ResponseEntity<>(new ErrorResponse(400, e.getMessage()), HttpStatus.BAD_REQUEST);
    }

    // IllegalArgumentException → 400 Bad Request (잘못된 파라미터)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handle(...) { ... }

    // @Valid 검증 실패 → 400 + 필드별 에러 메시지 Map 반환
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        // 예: {"username": "사용자 아이디는 필수입니다.", "password": "8자 이상이어야 합니다."}
        return ResponseEntity.badRequest().body(errors);
    }

    // 그 외 모든 에러 → 500 Internal Server Error
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception e) {
        e.printStackTrace();  // 서버 로그 출력
        return new ResponseEntity<>(new ErrorResponse(500, "서버 내부 오류"), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

**`@RestControllerAdvice`가 없으면 어떻게 되는가?**
예외가 터지면 Spring Boot가 기본 에러 페이지(HTML)를 반환한다.
REST API에서는 JSON 형식으로 에러를 반환해야 하기 때문에
`@RestControllerAdvice`로 모든 컨트롤러의 예외를 가로채서 JSON으로 변환한다.

---

### ErrorResponse — 에러 응답 DTO

```java
// exception/ErrorResponse.java
@Getter
@AllArgsConstructor
public class ErrorResponse {
    private int status;    // HTTP 상태 코드 (400, 404, 500...)
    private String message; // 사람이 읽을 수 있는 에러 메시지
}
```

에러가 발생하면 아래 형식으로 응답:
```json
{
  "status": 400,
  "message": "이미 저장된 콘텐츠입니다."
}
```
