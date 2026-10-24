package book_log.demo.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.test.util.ReflectionTestUtils;

import book_log.demo.config.JwtProperties;
import book_log.demo.domain.Member;

// 테스트 전용 가짜 JWT 설정. 실제 설정 파일 값은 쓰지 않는다
public final class TestJwt {

    public static final String FAKE_SECRET = base64("fake-test-only-secret-key-do-not-use-0123456789");
    public static final String OTHER_FAKE_SECRET = base64("another-fake-test-only-secret-key-9876543210");
    public static final long ACCESS_MS = 60_000L;
    public static final long REFRESH_MS = 600_000L;

    private TestJwt() {
    }

    public static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static JwtProperties properties(String secret, long accessMs, long refreshMs) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret);
        p.setAccessTokenExpiration(accessMs);
        p.setRefreshTokenExpiration(refreshMs);
        return p;
    }

    public static JwtTokenProvider provider() {
        return new JwtTokenProvider(properties(FAKE_SECRET, ACCESS_MS, REFRESH_MS));
    }

    public static Member member(Long id, String username) {
        Member m = Member.builder().username(username).password("hash-not-used").email(username + "@example.com")
                .build();
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}
