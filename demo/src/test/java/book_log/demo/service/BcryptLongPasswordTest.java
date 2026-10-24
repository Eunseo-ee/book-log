package book_log.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import book_log.demo.domain.Member;
import book_log.demo.dto.request.LoginRequestDto;
import book_log.demo.exception.InvalidCredentialsException;
import book_log.demo.repository.MemberRepository;
import book_log.demo.security.TestJwt;

// 사용 중인 Spring Security(6.4.x)의 BCrypt가 72바이트 초과 비밀번호를 어떻게 다루는지 실제로 확인한다
class BcryptLongPasswordTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    @DisplayName("이 버전의 BCrypt는 72바이트 초과 비밀번호를 예외 없이 뒤를 잘라 해시한다 (그래서 가입 시 바이트 검증이 필요)")
    void encodeSilentlyTruncatesAt72Bytes() {
        String hash = encoder.encode("a".repeat(72));

        assertThat(encoder.matches("a".repeat(72) + "TAIL-IS-IGNORED", hash)).isTrue();
    }

    @Test
    @DisplayName("matches에 72바이트를 크게 넘는 입력을 줘도 예외 없이 false/true만 돌려준다")
    void matchesDoesNotThrowOnVeryLongInput() {
        String hash = encoder.encode("a".repeat(30));

        assertThat(encoder.matches("가".repeat(64), hash)).isFalse();
    }

    @Test
    @DisplayName("매우 긴 비밀번호로 로그인하면 아이디가 있든 없든 같은 InvalidCredentialsException (추가 조치 불필요)")
    void loginWithVeryLongPasswordIsUniform() {
        MemberRepository repo = mock(MemberRepository.class);
        Member alice = Member.builder().username("alice").password(encoder.encode("plain-pass-1234"))
                .email("alice@example.com").build();
        when(repo.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(repo.findByUsername("nobody")).thenReturn(Optional.empty());
        AuthService service = new AuthService(repo, encoder, TestJwt.provider());
        String longPassword = "가".repeat(64);

        for (String username : new String[] { "alice", "nobody" }) {
            assertThatThrownBy(() -> service.login(new LoginRequestDto(username, longPassword)))
                    .isExactlyInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(InvalidCredentialsException.MESSAGE);
        }
    }
}
