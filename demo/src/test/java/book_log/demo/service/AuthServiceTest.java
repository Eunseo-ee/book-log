package book_log.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import book_log.demo.domain.Member;
import book_log.demo.domain.Role;
import book_log.demo.dto.request.LoginRequestDto;
import book_log.demo.dto.request.RegisterRequestDto;
import book_log.demo.dto.response.MemberResponseDto;
import book_log.demo.exception.DuplicateEmailException;
import book_log.demo.exception.DuplicateUsernameException;
import book_log.demo.exception.InvalidCredentialsException;
import book_log.demo.repository.MemberRepository;
import book_log.demo.security.TestJwt;
import book_log.demo.dto.response.LoginResponseDto;

import static org.mockito.Mockito.mock;

// DB 없이 Mockito만 쓴다. 인코더는 실제 BCrypt로 해시가 진짜 저장되는지 확인한다
class AuthServiceTest {

    private static final String RAW_PASSWORD = "plain-pass-1234";

    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4); // 테스트 속도를 위해 cost 최소
        authService = new AuthService(memberRepository, passwordEncoder, TestJwt.provider());
    }

    @Test
    @DisplayName("가입 성공: 비밀번호는 평문이 아닌 BCrypt 해시로 저장되고, 기본 role은 ROLE_USER")
    void registerStoresHash() {
        when(memberRepository.existsByUsername("alice")).thenReturn(false);
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(inv -> {
            Member m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 7L);
            return m;
        });

        MemberResponseDto result = authService.register(
                new RegisterRequestDto("alice", RAW_PASSWORD, "alice@example.com"));

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(captor.capture());
        Member saved = captor.getValue();

        assertThat(saved.getPassword()).isNotEqualTo(RAW_PASSWORD);
        assertThat(saved.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, saved.getPassword())).isTrue();
        assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("중복 아이디는 저장하지 않고 거부한다")
    void registerRejectsDuplicate() {
        when(memberRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequestDto("alice", RAW_PASSWORD, "alice@example.com")))
                .isInstanceOf(DuplicateUsernameException.class);

        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동시 가입으로 unique 제약에 걸려도 중복 아이디 예외로 변환한다")
    void registerConvertsConstraintViolation() {
        when(memberRepository.existsByUsername("alice")).thenReturn(false);
        when(memberRepository.saveAndFlush(any(Member.class)))
                .thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequestDto("alice", RAW_PASSWORD, "alice@example.com")))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    @DisplayName("이미 가입된 이메일은 저장하지 않고 거부한다")
    void registerRejectsDuplicateEmail() {
        when(memberRepository.existsByUsername("bob")).thenReturn(false);
        when(memberRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequestDto("bob", RAW_PASSWORD, "alice@example.com")))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");

        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("대소문자만 다른 이메일도 정규화해서 중복으로 거부한다")
    void registerRejectsEmailDifferingOnlyByCase() {
        when(memberRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequestDto("bob", RAW_PASSWORD, "Alice@Example.COM")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(memberRepository).existsByEmail("alice@example.com");
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("앞뒤 공백이 포함된 이메일도 정규화해서 중복으로 거부한다")
    void registerRejectsEmailWithSurroundingWhitespace() {
        when(memberRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequestDto("bob", RAW_PASSWORD, "  alice@example.com \t")))
                .isInstanceOf(DuplicateEmailException.class);

        verify(memberRepository).existsByEmail("alice@example.com");
        verify(memberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("이메일은 trim + 소문자로 정규화되어 저장된다")
    void registerStoresNormalizedEmail() {
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterRequestDto("bob", RAW_PASSWORD, "  Bob@Example.COM "));

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    @DisplayName("동시 가입으로 이메일 unique 제약에 걸리면 이메일 중복 예외로 변환한다")
    void registerConvertsEmailConstraintViolation() {
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(
                new DataIntegrityViolationException("unique", new org.hibernate.exception.ConstraintViolationException(
                        "unique", new java.sql.SQLException("duplicate", "23505"), Member.EMAIL_UNIQUE_CONSTRAINT)));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequestDto("bob", RAW_PASSWORD, "bob@example.com")))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("아이디 값에 email이 들어 있어도 이메일 제약 이름이 아니면 아이디 중복으로 본다")
    void registerKeepsUsernameConflictForOtherConstraint() {
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(
                new DataIntegrityViolationException("Key (username)=(email) already exists",
                        new org.hibernate.exception.ConstraintViolationException(
                                "Key (username)=(email) already exists", new java.sql.SQLException("duplicate", "23505"),
                                "uk_some_username")));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequestDto("email", RAW_PASSWORD, "bob@example.com")))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    @DisplayName("로그인 성공: id와 username을 돌려준다")
    void loginSuccess() {
        Member member = Member.builder()
                .username("alice")
                .password(passwordEncoder.encode(RAW_PASSWORD))
                .email("alice@example.com")
                .build();
        ReflectionTestUtils.setField(member, "id", 7L);
        when(memberRepository.findByUsername("alice")).thenReturn(Optional.of(member));

        LoginResponseDto result = authService.login(new LoginRequestDto("alice", RAW_PASSWORD));

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("로그인 실패: 없는 아이디와 틀린 비밀번호는 같은 예외, 같은 메시지")
    void loginFailureIsIndistinguishable() {
        Member member = Member.builder()
                .username("alice")
                .password(passwordEncoder.encode(RAW_PASSWORD))
                .email("alice@example.com")
                .build();
        when(memberRepository.findByUsername("alice")).thenReturn(Optional.of(member));
        when(memberRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        Throwable unknownUser = org.assertj.core.api.Assertions.catchThrowable(
                () -> authService.login(new LoginRequestDto("nobody", RAW_PASSWORD)));
        Throwable wrongPassword = org.assertj.core.api.Assertions.catchThrowable(
                () -> authService.login(new LoginRequestDto("alice", "wrong-pass-0000")));

        assertThat(unknownUser).isInstanceOf(InvalidCredentialsException.class);
        assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
        assertThat(unknownUser.getMessage()).isEqualTo(wrongPassword.getMessage())
                .isEqualTo(InvalidCredentialsException.MESSAGE);
        assertThat(unknownUser.getMessage()).doesNotContain(RAW_PASSWORD);
    }
}
