package book_log.demo.security;

import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import book_log.demo.config.JwtProperties;
import book_log.demo.domain.Member;
import book_log.demo.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.decodeSecret());
        this.accessExpirationMs = properties.getAccessTokenExpiration();
        this.refreshExpirationMs = properties.getRefreshTokenExpiration();
    }

    // 토큰에서 꺼낸 값. 필터가 DB 조회 없이 AuthenticatedMember를 만드는 재료
    public record TokenClaims(Long userId, String username, Role role) {
    }

    public String createAccessToken(Member member) {
        return create(member, TokenType.ACCESS, accessExpirationMs);
    }

    public String createRefreshToken(Member member) {
        return create(member, TokenType.REFRESH, refreshExpirationMs);
    }

    private String create(Member member, TokenType type, long expirationMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim(CLAIM_USERNAME, member.getUsername())
                .claim(CLAIM_ROLE, member.getRole().name())
                .claim(CLAIM_TYPE, type.claimValue())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    // 서명·만료·형식·type을 모두 확인한다. 실패하면 이유만 로그에 남기고(토큰 값은 남기지 않는다) empty
    public Optional<TokenClaims> parse(String token, TokenType expectedType) {
        if (token == null || token.isBlank()) {
            return fail("토큰이 비어 있음");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

            if (!expectedType.claimValue().equals(claims.get(CLAIM_TYPE, String.class))) {
                return fail("토큰 type 불일치 (기대: " + expectedType.claimValue() + ")");
            }
            String username = claims.get(CLAIM_USERNAME, String.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            if (claims.getSubject() == null || username == null || role == null) {
                return fail("필수 claim 누락");
            }
            return Optional.of(new TokenClaims(Long.valueOf(claims.getSubject()), username, Role.valueOf(role)));
        } catch (ExpiredJwtException e) {
            return fail("만료된 토큰");
        } catch (SignatureException e) {
            return fail("서명 불일치");
        } catch (JwtException e) {
            return fail("형식 오류 또는 지원하지 않는 토큰 (" + e.getClass().getSimpleName() + ")");
        } catch (RuntimeException e) {
            // subject가 숫자가 아니거나 role이 알 수 없는 값인 경우 등. 필터가 예외를 던지면 안 되므로 여기서 모두 막는다
            return fail("claim 값 오류 (" + e.getClass().getSimpleName() + ")");
        }
    }

    private Optional<TokenClaims> fail(String reason) {
        log.warn("JWT 검증 실패: {}", reason);
        return Optional.empty();
    }
}
