package book_log.demo.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import book_log.demo.domain.Member;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtAuthenticationFilterTest {

    private final JwtTokenProvider provider = TestJwt.provider();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);
    private final Member member = TestJwt.member(7L, "alice");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // 필터를 실행하고 다음 필터(체인)가 호출됐는지 돌려준다. 필터가 예외를 던지면 테스트가 실패한다
    private boolean run(JwtAuthenticationFilter target, String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockFilterChain chain = new MockFilterChain();
        target.doFilter(request, new MockHttpServletResponse(), chain);
        return chain.getRequest() != null;
    }

    @Test
    @DisplayName("유효한 access 토큰이면 SecurityContext에 AuthenticatedMember가 들어가고 체인이 계속된다")
    void validAccessToken() throws Exception {
        boolean continued = run(filter, "Bearer " + provider.createAccessToken(member));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(continued).isTrue();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(new AuthenticatedMember(7L, "alice"));
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Bearer 접두어는 대소문자를 구분하지 않는다")
    void bearerCaseInsensitive() throws Exception {
        run(filter, "bearer " + provider.createAccessToken(member));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("헤더 없음 / 잘못된 형식 / 만료 / 위조 / refresh 토큰은 인증 없이 체인을 계속하고 예외도 없다")
    void invalidTokensAreNotAuthenticated() throws Exception {
        JwtTokenProvider expiring = new JwtTokenProvider(
                TestJwt.properties(TestJwt.FAKE_SECRET, -1_000L, TestJwt.REFRESH_MS));
        JwtTokenProvider otherSecret = new JwtTokenProvider(
                TestJwt.properties(TestJwt.OTHER_FAKE_SECRET, TestJwt.ACCESS_MS, TestJwt.REFRESH_MS));
        String unsigned = Jwts.builder().subject("7").claim("username", "alice").claim("role", "ROLE_USER")
                .claim("type", "access").expiration(new Date(System.currentTimeMillis() + 60_000)).compact();
        String valid = provider.createAccessToken(member);

        String[] headers = {
                null,
                "",
                "Bearer",
                "Bearer ",
                "Basic " + valid,
                valid, // 접두어 없음
                "Bearer garbage",
                "Bearer a.b.c",
                "Bearer " + expiring.createAccessToken(member),
                "Bearer " + otherSecret.createAccessToken(member),
                "Bearer " + valid.substring(0, valid.length() - 3) + "xyz",
                "Bearer " + unsigned,
                "Bearer " + provider.createRefreshToken(member),
        };

        for (String header : headers) {
            SecurityContextHolder.clearContext();
            String label = header == null ? "(헤더 없음)" : header.substring(0, Math.min(header.length(), 20));

            assertThat(run(filter, header)).as("체인 계속: %s", label).isTrue();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).as("인증 없어야 함: %s", label).isNull();
        }
    }

    @Test
    @DisplayName("토큰 검증 중 예상 못한 예외가 나도 필터는 예외를 던지지 않고 체인을 계속한다")
    void unexpectedProviderFailure() throws Exception {
        JwtTokenProvider broken = new JwtTokenProvider(
                TestJwt.properties(TestJwt.FAKE_SECRET, TestJwt.ACCESS_MS, TestJwt.REFRESH_MS)) {
            @Override
            public Optional<TokenClaims> parse(String token, TokenType expectedType) {
                throw new IllegalStateException("boom");
            }
        };

        assertThat(run(new JwtAuthenticationFilter(broken), "Bearer anything")).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("같은 키로 직접 만든 type=refresh 토큰도 인증되지 않는다")
    void handCraftedRefreshToken() throws Exception {
        var key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TestJwt.FAKE_SECRET));
        String refresh = Jwts.builder().subject("7").claim("username", "alice").claim("role", "ROLE_USER")
                .claim("type", "refresh").expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key)
                .compact();

        run(filter, "Bearer " + refresh);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
