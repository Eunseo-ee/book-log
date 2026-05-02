# JWT + Spring Security 트러블슈팅 가이드

> 이 프로젝트(Spring Boot 3.x + jjwt 0.12.x + PostgreSQL)에서 실제로 발생 가능한 에러 기준으로 정리.

---

## 목차

1. [401 Unauthorized — 토큰을 보냈는데도 인증 실패](#1-401-unauthorized--토큰을-보냈는데도-인증-실패)
2. [403 Forbidden — /api/auth/login, /register 에서도 차단](#2-403-forbidden--apiauthlogin-register-에서도-차단)
3. [NoSuchBeanDefinitionException: PasswordEncoder](#3-nosuchbeandefinitionexception-passwordencoder)
4. [BeanCurrentlyInCreationException — 순환 참조](#4-beancurrentlyincreationexception--순환-참조)
5. [io.jsonwebtoken.security.WeakKeyException — 시크릿 키 길이 부족](#5-iojsonwebtokensecurityweakkeyexception--시크릿-키-길이-부족)
6. [JWT expired at ... — 토큰 만료](#6-jwt-expired-at---토큰-만료)
7. [JWT signature does not match — 위변조 감지](#7-jwt-signature-does-not-match--위변조-감지)
8. [IllegalArgumentException: JWT String argument cannot be null](#8-illegalargumentexception-jwt-string-argument-cannot-be-null)
9. [401 on CORS Preflight (OPTIONS 요청)](#9-401-on-cors-preflight-options-요청)
10. [Hibernate: column id is of type uuid but expression is of type bigint](#10-hibernate-column-id-is-of-type-uuid-but-expression-is-of-type-bigint)
11. [Bind to environment failed: jwt.secret](#11-bind-to-environment-failed-jwtsecret)
12. [UsernameNotFoundException after login — DB에 저장됐는데 못 찾음](#12-usernamenotfoundexception-after-login--db에-저장됐는데-못-찾음)

---

## 1. 401 Unauthorized — 토큰을 보냈는데도 인증 실패

### 문제 상황
```
HTTP 401 Unauthorized
```
로그인으로 받은 accessToken을 Authorization 헤더에 담아 보냈는데도 401이 반환된다.

### 원인 분석
1. **헤더 형식 오류**: `Bearer `(공백 포함) 없이 토큰만 전송
   ```
   Authorization: eyJhbGci...   ← 잘못된 형식
   Authorization: Bearer eyJhbGci...   ← 올바른 형식
   ```
2. **JwtAuthenticationFilter가 SecurityConfig에 등록되지 않음**: 필터가 빠져서 토큰을 읽는 코드가 실행되지 않음
3. **SecurityContext에 Authentication이 저장되지 않음**: 토큰 검증은 통과했지만 `SecurityContextHolder.getContext().setAuthentication()` 미호출

### 해결 방법
SecurityConfig에 필터가 등록되어 있는지 확인:
```java
.addFilterBefore(
    new JwtAuthenticationFilter(jwtTokenProvider),
    UsernamePasswordAuthenticationFilter.class  // 이 앞에 등록해야 함
)
```

JwtAuthenticationFilter 내부에서 Authentication 저장 확인:
```java
if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
    Authentication auth = jwtTokenProvider.getAuthentication(token);
    SecurityContextHolder.getContext().setAuthentication(auth);  // 이 줄이 반드시 있어야 함
}
```

### 재발 방지책
- API 클라이언트(Postman, 프론트엔드)에서 헤더 형식을 `Bearer {token}` 템플릿으로 고정
- 통합 테스트에서 `Authorization` 헤더를 포함한 인증 흐름 테스트 추가

---

## 2. 403 Forbidden — /api/auth/login, /register 에서도 차단

### 문제 상황
```
HTTP 403 Forbidden
POST /api/auth/register
```
토큰도 없는 회원가입/로그인 요청에서 403이 반환된다.

### 원인 분석
1. **SecurityConfig `permitAll()` 경로가 잘못 설정됨**: 
   ```java
   .requestMatchers("/api/auth/**").permitAll()   // 올바름
   .requestMatchers("/api/auth").permitAll()       // /register, /login 미포함 → 잘못됨
   ```
2. **CSRF 설정이 활성화된 상태**: REST API에 CSRF 토큰이 없어서 POST 요청이 차단됨
3. **`anyRequest().authenticated()` 순서 문제**: `permitAll()` 설정보다 `authenticated()`가 먼저 적용되는 경우

### 해결 방법
```java
http
    .csrf(AbstractHttpConfigurer::disable)  // REST API이므로 CSRF 비활성화 필수
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/**").permitAll()  // 와일드카드 /**로 하위 경로 모두 허용
        .anyRequest().authenticated()                 // permitAll 다음에 위치해야 함
    )
```

### 재발 방지책
- `permitAll()` 경로는 항상 `anyRequest()` 보다 앞에 선언
- 개발 초기 단계에서 `.anyRequest().permitAll()`로 일단 전체 허용 후 점진적으로 인증 추가

---

## 3. NoSuchBeanDefinitionException: PasswordEncoder

### 문제 상황
```
NoSuchBeanDefinitionException: No qualifying bean of type 
'org.springframework.security.crypto.password.PasswordEncoder' available
```
AuthService 시작 시 애플리케이션이 뜨지 않는다.

### 원인 분석
`SecurityConfig`에 `PasswordEncoder` 빈이 등록되지 않았거나,
`AuthService`에서 주입받는 타입이 `BCryptPasswordEncoder`가 아닌 `PasswordEncoder` 인터페이스임에도
구현체 빈이 없는 상황.

### 해결 방법
`SecurityConfig`에 빈 등록 확인:
```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```
`BCryptPasswordEncoder`를 직접 `new`로 생성하면 Spring 관리 빈이 아니므로
`AuthService`에 주입이 안 된다. 반드시 `@Bean`으로 등록해야 한다.

### 재발 방지책
- 암호화 관련 빈은 `SecurityConfig` 한 곳에서만 관리
- `PasswordEncoder` 인터페이스 타입으로 주입받아 구현체 교체 유연성 확보

---

## 4. BeanCurrentlyInCreationException — 순환 참조

### 문제 상황
```
BeanCurrentlyInCreationException: Error creating bean with name 'securityConfig': 
Requested bean is currently in creation: Is there an unresolvable circular reference?
```

### 원인 분석
`SecurityConfig` → `UserDetailsService` 주입 → `UserDetailsService` 구현체가 내부적으로
`AuthenticationManager` 참조 → `AuthenticationManager`가 다시 `SecurityConfig` 참조하는 순환 구조.

Spring Boot 2.6 이후 순환 참조가 기본 비활성화(`spring.main.allow-circular-references: false`)되어
더 자주 발생한다.

### 해결 방법
`@Lazy` 어노테이션으로 지연 초기화:
```java
public SecurityConfig(@Lazy JwtTokenProvider jwtTokenProvider) {
    this.jwtTokenProvider = jwtTokenProvider;
}
```
또는 `CustomUserDetailsService`와 `SecurityConfig`의 의존 방향을 단방향으로 정리.
`JwtTokenProvider`가 `UserDetailsService`를 주입받는 현재 구조에서는
`SecurityConfig`가 `JwtTokenProvider`만 직접 의존하면 순환이 발생하지 않는다.

### 재발 방지책
- Security 설정 클래스는 다른 서비스 빈을 직접 주입받지 않도록 설계
- 순환 참조 허용(`allow-circular-references: true`)은 임시 방편이므로 사용 금지

---

## 5. io.jsonwebtoken.security.WeakKeyException — 시크릿 키 길이 부족

### 문제 상황
```
io.jsonwebtoken.security.WeakKeyException: The specified key byte array is 248 bits 
which is not secure enough for any JWT HMAC-SHA algorithm.
The JWT JWA Specification (RFC 7518, Section 3.2) states that keys used with 
HMAC-SHA algorithms MUST have a size >= 256 bits (the key size must be greater than 
or equal to the hash output size).
```

### 원인 분석
`application.yml`의 `jwt.secret` 값이 BASE64 디코딩 후 256비트(32바이트) 미만인 경우.
HMAC-SHA256은 최소 256비트 키를 요구한다.

### 해결 방법
아래 명령으로 안전한 256비트 이상의 BASE64 키를 새로 생성:
```bash
# openssl 방식 (Linux/Mac)
openssl rand -base64 32

# Java 방식
import java.util.Base64;
import java.security.SecureRandom;
byte[] key = new byte[32]; // 256비트
new SecureRandom().nextBytes(key);
System.out.println(Base64.getEncoder().encodeToString(key));
```
생성된 값을 `application.yml`에 적용:
```yaml
jwt:
  secret: "생성된_256비트_이상_BASE64_문자열"
```

### 재발 방지책
- 키 생성 스크립트를 프로젝트 `scripts/gen-secret.sh`로 관리
- 운영 환경에서는 환경변수 `${JWT_SECRET}`로 주입하고 yml에 하드코딩 금지

---

## 6. JWT expired at ... — 토큰 만료

### 문제 상황
```
io.jsonwebtoken.ExpiredJwtException: JWT expired at 2026-05-01T10:00:00Z. 
Current time: 2026-05-01T10:16:00Z, a difference of 960000 milliseconds. 
Allowed clock skew: 0 milliseconds.
```

### 원인 분석
accessToken의 만료 시간(15분)이 지난 후 요청을 보낸 경우.
서버-클라이언트 간 시스템 시계 불일치도 원인이 될 수 있다.

### 해결 방법
클라이언트에서 accessToken 만료 시 refreshToken으로 재발급 요청:
```
POST /api/auth/refresh
Authorization: Bearer {refreshToken}
```
(현재 프로젝트에서 refresh 엔드포인트는 추후 구현 예정)

개발 중 테스트 편의를 위해 만료 시간 임시 확장:
```yaml
jwt:
  access-token-expiration: 86400000  # 개발 중만 사용 (24시간)
```

### 재발 방지책
- 프론트엔드에서 401 응답 시 자동으로 refresh 요청을 보내는 인터셉터 구현
- accessToken 만료 전 프론트에서 만료 시각 계산 후 선제적 갱신

---

## 7. JWT signature does not match — 위변조 감지

### 문제 상황
```
io.jsonwebtoken.security.SignatureException: JWT signature does not match 
locally computed signature. JWT validity cannot be asserted and should not be trusted.
```

### 원인 분석
1. **서버 재시작 후 시크릿 키 변경**: 이전 키로 발급한 토큰을 새 키로 검증하면 서명 불일치
2. **여러 서버 인스턴스 간 키 불일치**: 각 인스턴스가 다른 시크릿 키 사용
3. **토큰 변조**: 클라이언트에서 payload를 수정한 경우
4. **개발 중 yml 수정**: `jwt.secret` 값을 바꾼 뒤 기존 토큰으로 테스트

### 해결 방법
- 기존 토큰 폐기 후 재로그인하여 새 토큰 발급
- 여러 서버 인스턴스라면 `jwt.secret`을 환경변수로 통일:
  ```yaml
  jwt:
    secret: ${JWT_SECRET}
  ```

### 재발 방지책
- 시크릿 키는 환경변수로 관리하여 서버 재시작/스케일아웃에도 동일한 키 유지
- 운영 환경에서 시크릿 키 변경 시 모든 사용자 재로그인 안내 필요

---

## 8. IllegalArgumentException: JWT String argument cannot be null

### 문제 상황
```
java.lang.IllegalArgumentException: JWT String argument cannot be null or empty.
```

### 원인 분석
`JwtAuthenticationFilter`에서 Authorization 헤더가 없거나 `Bearer ` 이후 값이 없는데
`validateToken()` 또는 `parseClaims()`에 null 또는 빈 문자열이 전달된 경우.

### 해결 방법
`JwtAuthenticationFilter`에서 null 체크 확인:
```java
String token = resolveToken(request);

// StringUtils.hasText()가 null, "", "  " 모두 걸러줌
if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
    ...
}
```
`resolveToken()`의 반환값이 null인지 확인:
```java
private String resolveToken(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
        return bearer.substring(7);
    }
    return null;  // null 반환 → 위의 hasText() 체크로 처리됨
}
```

### 재발 방지책
- 외부에서 들어오는 토큰은 항상 null/empty 체크 후 처리
- `validateToken()` 내부에서도 방어적으로 처리 가능:
  ```java
  public boolean validateToken(String token) {
      if (!StringUtils.hasText(token)) return false;
      // ... 기존 로직
  }
  ```

---

## 9. 401 on CORS Preflight (OPTIONS 요청)

### 문제 상황
```
HTTP 401 Unauthorized
OPTIONS /api/contents
Access-Control-Allow-Origin 헤더 없음
```
브라우저에서 API 호출 시 실제 요청 전 보내는 preflight 요청이 401로 차단된다.

### 원인 분석
Spring Security가 OPTIONS 요청도 인증 필터를 통과시키려 하는데,
preflight 요청에는 Authorization 헤더가 없어 401이 반환됨.
또한 CORS 설정이 없으면 `Access-Control-Allow-Origin` 헤더가 응답에 포함되지 않아
브라우저가 에러로 처리함.

### 해결 방법
SecurityConfig에 CORS 설정 추가:
```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:3000"));  // 프론트엔드 주소
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```
```java
http
    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
    // OPTIONS preflight는 인증 없이 통과
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        ...
    )
```

### 재발 방지책
- 개발 초기부터 CORS 설정을 SecurityConfig에 포함
- 운영 환경에서는 `allowedOrigins`를 정확한 도메인으로 제한 (와일드카드 `*` 사용 금지)

---

## 10. Hibernate: column id is of type uuid but expression is of type bigint

### 문제 상황
```
ERROR: operator does not exist: uuid = bigint
Hint: No operator matches the given name and argument types. 
You might need to add explicit type casts.
```

### 원인 분석
`Member` 엔티티의 id 타입을 `Long` → `UUID`로 변경했는데
DB 테이블의 기존 `id` 컬럼 타입이 여전히 `bigint`(IDENTITY 방식)인 경우.

또는 JPA 설정에서 UUID 컬럼 정의가 누락된 경우:
```java
// 잘못된 경우
@Id @GeneratedValue(strategy = GenerationType.UUID)
private UUID id;

// 올바른 경우 (PostgreSQL UUID 타입 명시)
@Id @GeneratedValue(strategy = GenerationType.UUID)
@Column(columnDefinition = "uuid")
private UUID id;
```

### 해결 방법
`ddl-auto: create`로 테이블을 재생성하거나, 직접 마이그레이션:
```sql
-- 기존 테이블 삭제 후 재생성 (개발 환경)
DROP TABLE IF EXISTS members CASCADE;

-- 또는 컬럼 타입 변경 (데이터 보존 필요 시)
ALTER TABLE members ALTER COLUMN id TYPE uuid USING id::text::uuid;
```

### 재발 방지책
- 엔티티 타입 변경 시 `ddl-auto: validate`로 실제 스키마와 비교 확인
- 팀 프로젝트에서는 Flyway 또는 Liquibase 같은 마이그레이션 도구 사용

---

## 11. Bind to environment failed: jwt.secret

### 문제 상황
```
Caused by: java.lang.IllegalArgumentException: Could not resolve placeholder 
'jwt.secret' in value "${jwt.secret}"
```
또는:
```
Failed to bind properties under 'jwt' to java.lang.String
```

### 원인 분석
1. **application.yml merge conflict 잔재**: `<<<<<<`, `=======`, `>>>>>>>` 마커가 남아있음
2. **yml 들여쓰기 오류**: yml은 탭이 아닌 스페이스로 들여쓰기해야 함
3. **application-api-key.yml 미생성**: `profiles.include: api-key` 설정인데 해당 파일이 없음

### 해결 방법
`application.yml` 충돌 마커 제거 확인:
```yaml
# 잘못된 경우
<<<<<<< HEAD
jwt:
  secret: "oldvalue"
=======
jwt:
  secret: "newvalue"
>>>>>>> feature/security

# 올바른 경우
jwt:
  secret: "newvalue"
  access-token-expiration: 900000
  refresh-token-expiration: 604800000
```
누락된 프로파일 파일 생성:
```
src/main/resources/application-api-key.yml
```

### 재발 방지책
- yml 파일은 항상 스페이스(2칸)로 들여쓰기
- merge 후 yml 파일을 우선적으로 검사

---

## 12. UsernameNotFoundException after login — DB에 저장됐는데 못 찾음

### 문제 상황
```
org.springframework.security.core.userdetails.UsernameNotFoundException: 
사용자를 찾을 수 없습니다: testuser
```
회원가입 직후 바로 로그인하면 실패한다.

### 원인 분석
1. **트랜잭션 미커밋**: `register()`의 `@Transactional`이 커밋되기 전에 `loadUserByUsername()`이 호출
   - 보통 `register()` 성공 응답 후 곧바로 `login()` 을 호출하면 발생하지 않지만,
     테스트 코드에서 같은 트랜잭션 내에서 연속 호출 시 발생 가능
2. **DB 연결 문제**: Supabase connection pool 설정이 없어 연결이 끊어진 후 재조회 실패

### 해결 방법
테스트 코드에서 트랜잭션 분리:
```java
// 잘못된 경우: 한 트랜잭션 안에서 save → findByUsername
@Test
@Transactional
void registerAndLogin() { ... }

// 올바른 경우: 각각 별도 요청으로 분리 (통합 테스트)
@Test
void register() { ... }  // 커밋됨

@Test
void login() { ... }     // 위에서 커밋된 데이터 조회
```

Supabase 연결 안정성을 위해 `application.yml`에 datasource 설정 추가:
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
      maximum-pool-size: 5
```

### 재발 방지책
- 통합 테스트는 `@Transactional` 없이 실제 HTTP 요청 방식(MockMvc, RestAssured)으로 작성
- Supabase pooler 주소와 포트(6543) 사용 확인
