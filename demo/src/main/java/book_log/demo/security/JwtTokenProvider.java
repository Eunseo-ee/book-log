package book_log.demo.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 토큰 생성 / 검증 / 파싱 담당 클래스
 *
 * 토큰 종류:
 * - accessToken  : API 호출 시 인증에 사용 (짧은 만료 시간: 15분)
 * - refreshToken : accessToken 만료 시 재발급 요청에 사용 (긴 만료 시간: 7일)
 *
 * 토큰 구조 (JWT):
 *   Header.Payload.Signature
 *   - Header  : 알고리즘 정보 (HS256 등)
 *   - Payload : subject(username), 발급시간, 만료시간
 *   - Signature: secretKey로 서명 → 위변조 감지
 *
 * 사용된 라이브러리: io.jsonwebtoken:jjwt (0.12.x)
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /**
     * HMAC-SHA 알고리즘용 비밀 키
     * application.yml의 jwt.secret을 BASE64 디코딩하여 생성
     */
    private final SecretKey secretKey;

    /** accessToken 만료 시간 (밀리초): 15분 = 900,000ms */
    private final long accessTokenExpiration;

    /** refreshToken 만료 시간 (밀리초): 7일 = 604,800,000ms */
    private final long refreshTokenExpiration;

    /**
     * Spring Security의 UserDetailsService
     * getAuthentication() 호출 시 DB에서 최신 사용자 정보(권한 등)를 로드하기 위해 사용
     */
    private final UserDetailsService userDetailsService;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
            UserDetailsService userDetailsService
    ) {
        // BASE64로 인코딩된 시크릿 키를 디코딩해서 HMAC-SHA 키 생성
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.userDetailsService = userDetailsService;
    }

    /**
     * accessToken 생성
     *
     * Payload에 username(subject)과 발급/만료 시각을 담아 서명.
     * 만료 시간: 15분
     *
     * @param username 토큰에 담을 사용자 아이디
     * @return 서명된 JWT 문자열
     */
    public String generateAccessToken(String username) {
        return buildToken(username, accessTokenExpiration);
    }

    /**
     * refreshToken 생성
     *
     * accessToken과 동일한 구조지만 만료 시간이 더 길다.
     * 만료 시간: 7일
     *
     * @param username 토큰에 담을 사용자 아이디
     * @return 서명된 JWT 문자열
     */
    public String generateRefreshToken(String username) {
        return buildToken(username, refreshTokenExpiration);
    }

    /**
     * JWT 토큰 공통 생성 로직
     *
     * @param username   토큰 subject (사용자 아이디)
     * @param expiration 만료 시간 (밀리초)
     * @return 서명된 JWT 문자열
     */
    private String buildToken(String username, long expiration) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)        // 토큰 소유자 (사용자 아이디)
                .issuedAt(now)            // 발급 시각
                .expiration(expiry)       // 만료 시각
                .signWith(secretKey)      // HMAC-SHA 서명 (위변조 감지용)
                .compact();
    }

    /**
     * 토큰에서 username(subject) 추출
     *
     * @param token JWT 문자열
     * @return 토큰에 담긴 사용자 아이디
     */
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 토큰 유효성 검증
     *
     * - 만료 여부
     * - 서명 일치 여부
     * - 토큰 형식 정상 여부
     *
     * @param token 검증할 JWT 문자열
     * @return 유효하면 true, 유효하지 않으면 false
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT 토큰이 만료되었습니다: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 토큰 형식입니다: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("잘못된 구조의 JWT 토큰입니다: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 비어있거나 null입니다: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 토큰에서 Authentication 객체 생성
     *
     * JwtAuthenticationFilter에서 SecurityContext에 저장할 Authentication을 만들 때 사용.
     * DB에서 최신 UserDetails를 로드해 권한 정보를 반영한다.
     *
     * @param token 유효성이 검증된 JWT 문자열
     * @return Spring Security의 Authentication 객체
     */
    public Authentication getAuthentication(String token) {
        String username = getUsername(token);
        // DB에서 최신 사용자 정보 (권한 목록 포함) 로드
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        // credentials(비밀번호)는 null로 전달 → 이미 토큰으로 인증 완료, 비밀번호 재확인 불필요
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    /**
     * JWT 파싱 및 서명 검증
     *
     * 토큰이 변조되었거나 만료된 경우 예외가 발생한다.
     *
     * @param token JWT 문자열
     * @return 토큰의 Payload (Claims)
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)   // 서명 검증에 사용할 키 설정
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
