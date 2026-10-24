package book_log.demo.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import book_log.demo.domain.Member;
import book_log.demo.domain.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtTokenProviderTest {

    private final JwtTokenProvider provider = TestJwt.provider();
    private final Member member = TestJwt.member(7L, "alice");
    private final SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TestJwt.FAKE_SECRET));

    private static Date inOneMinute() {
        return new Date(System.currentTimeMillis() + 60_000);
    }

    @Test
    @DisplayName("생성한 access 토큰을 파싱하면 id/username/role이 그대로 나온다")
    void accessRoundTrip() {
        String token = provider.createAccessToken(member);

        Optional<JwtTokenProvider.TokenClaims> claims = provider.parse(token, TokenType.ACCESS);

        assertThat(claims).isPresent();
        assertThat(claims.get().userId()).isEqualTo(7L);
        assertThat(claims.get().username()).isEqualTo("alice");
        assertThat(claims.get().role()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    @DisplayName("생성한 refresh 토큰도 refresh로 검증하면 통과한다")
    void refreshRoundTrip() {
        assertThat(provider.parse(provider.createRefreshToken(member), TokenType.REFRESH)).isPresent();
    }

    @Test
    @DisplayName("만료된 토큰은 거부한다")
    void expiredToken() {
        JwtTokenProvider expiring = new JwtTokenProvider(
                TestJwt.properties(TestJwt.FAKE_SECRET, -1_000L, TestJwt.REFRESH_MS));

        assertThat(provider.parse(expiring.createAccessToken(member), TokenType.ACCESS)).isEmpty();
    }

    @Test
    @DisplayName("서명을 변조한 토큰은 거부한다")
    void tamperedSignature() {
        String token = provider.createAccessToken(member);
        String tampered = token.substring(0, token.length() - 3) + "xyz";

        assertThat(provider.parse(tampered, TokenType.ACCESS)).isEmpty();
    }

    @Test
    @DisplayName("payload를 바꿔치기한 토큰(서명은 그대로)은 거부한다")
    void tamperedPayload() {
        String[] parts = provider.createAccessToken(member).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"1\",\"username\":\"admin\",\"role\":\"ROLE_ADMIN\",\"type\":\"access\"}".getBytes());

        assertThat(provider.parse(parts[0] + "." + forgedPayload + "." + parts[2], TokenType.ACCESS)).isEmpty();
    }

    @Test
    @DisplayName("다른 시크릿으로 만든 토큰은 거부한다")
    void otherSecret() {
        JwtTokenProvider other = new JwtTokenProvider(
                TestJwt.properties(TestJwt.OTHER_FAKE_SECRET, TestJwt.ACCESS_MS, TestJwt.REFRESH_MS));

        assertThat(provider.parse(other.createAccessToken(member), TokenType.ACCESS)).isEmpty();
    }

    @Test
    @DisplayName("type 불일치: refresh를 access로, access를 refresh로 검증하면 거부한다")
    void typeMismatch() {
        assertThat(provider.parse(provider.createRefreshToken(member), TokenType.ACCESS)).isEmpty();
        assertThat(provider.parse(provider.createAccessToken(member), TokenType.REFRESH)).isEmpty();
    }

    @Test
    @DisplayName("type claim이 없는 토큰은 거부한다")
    void missingTypeClaim() {
        String noType = Jwts.builder().subject("7").claim("username", "alice").claim("role", "ROLE_USER")
                .expiration(inOneMinute()).signWith(key).compact();

        assertThat(provider.parse(noType, TokenType.ACCESS)).isEmpty();
    }

    @Test
    @DisplayName("subject가 숫자가 아니거나 role이 알 수 없는 값이어도 예외 없이 거부한다")
    void badClaimValues() {
        String badSubject = Jwts.builder().subject("not-a-number").claim("username", "a").claim("role", "ROLE_USER")
                .claim("type", "access").expiration(inOneMinute()).signWith(key).compact();
        String badRole = Jwts.builder().subject("1").claim("username", "a").claim("role", "ROLE_NOPE")
                .claim("type", "access").expiration(inOneMinute()).signWith(key).compact();

        assertThat(provider.parse(badSubject, TokenType.ACCESS)).isEmpty();
        assertThat(provider.parse(badRole, TokenType.ACCESS)).isEmpty();
    }

    @Test
    @DisplayName("alg=none(서명 없는) 토큰은 거부한다")
    void unsignedToken() {
        String unsigned = Jwts.builder().subject("7").claim("username", "alice").claim("role", "ROLE_ADMIN")
                .claim("type", "access").expiration(inOneMinute()).compact();

        assertThat(provider.parse(unsigned, TokenType.ACCESS)).isEmpty();
    }

    @Test
    @DisplayName("형식이 깨진 문자열 / null / 빈 문자열은 예외 없이 거부한다")
    void malformed() {
        for (String bad : new String[] { "garbage", "a.b.c", "....", "   ", "", null }) {
            assertThat(provider.parse(bad, TokenType.ACCESS)).isEmpty();
        }
    }
}
