package book_log.demo.service;

import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import book_log.demo.domain.Member;
import book_log.demo.dto.response.AccessTokenResponseDto;
import book_log.demo.dto.request.RefreshRequestDto;
import book_log.demo.dto.response.LoginResponseDto;
import book_log.demo.exception.InvalidTokenException;
import book_log.demo.security.JwtTokenProvider;
import book_log.demo.security.TokenType;
import book_log.demo.dto.request.LoginRequestDto;
import book_log.demo.dto.request.RegisterRequestDto;
import book_log.demo.dto.response.MemberResponseDto;
import book_log.demo.exception.DuplicateEmailException;
import book_log.demo.exception.DuplicateUsernameException;
import book_log.demo.exception.InvalidCredentialsException;
import book_log.demo.repository.MemberRepository;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    // 없는 아이디도 BCrypt 비교를 한 번 수행해 응답 시간 차이로 아이디 존재 여부가 드러나지 않게 한다
    private final String dummyHash;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public MemberResponseDto register(RegisterRequestDto request) {
        // 대소문자·앞뒤 공백만 다른 이메일로 중복 검사를 우회하지 못하게 저장·비교 전에 정규화한다
        String email = normalizeEmail(request.getEmail());

        if (memberRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUsernameException();
        }
        // 친절한 에러 메시지용 검사. 동시 가입은 이 검사를 둘 다 통과하므로 최종 방어선은 DB unique 제약이다
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        Member member = Member.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(email)
                .build();

        try {
            // 동시 가입으로 exists 검사를 통과해도 unique 제약에서 걸리도록 즉시 flush
            return MemberResponseDto.from(memberRepository.saveAndFlush(member));
        } catch (DataIntegrityViolationException e) {
            // 이메일 제약 위반만 이메일 중복으로, 그 외(아이디 unique 등)는 기존대로 아이디 중복으로 본다
            if (isEmailUniqueViolation(e)) {
                throw new DuplicateEmailException();
            }
            throw new DuplicateUsernameException();
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // 제약 이름으로 구분한다. 메시지 문자열에서 "email"을 찾으면 아이디 값에 email이 들어간 경우를 잘못 판정한다
    private static boolean isEmailUniqueViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null
                    && cve.getConstraintName().toLowerCase(Locale.ROOT).contains(Member.EMAIL_UNIQUE_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto request) {
        Member member = memberRepository.findByUsername(request.getUsername()).orElse(null);

        if (member == null) {
            passwordEncoder.matches(request.getPassword(), dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new InvalidCredentialsException();
        }
        return LoginResponseDto.of(member, tokenProvider.createAccessToken(member),
                tokenProvider.createRefreshToken(member));
    }

    // refresh 토큰으로 새 access 토큰만 발급한다 (refresh는 회전하지 않는다).
    // 서버가 refresh를 저장하지 않으므로 토큰 자체는 만료까지 폐기할 수 없고, 회원이 아직 있는지만 DB로 확인한다.
    // 실패 원인(위조/만료/access 토큰/회원 없음)은 구분하지 않고 같은 예외로 응답한다
    @Transactional(readOnly = true)
    public AccessTokenResponseDto refresh(RefreshRequestDto request) {
        JwtTokenProvider.TokenClaims claims = tokenProvider.parse(request.getRefreshToken(), TokenType.REFRESH)
                .orElseThrow(InvalidTokenException::new);
        Member member = memberRepository.findById(claims.userId()).orElseThrow(InvalidTokenException::new);
        return new AccessTokenResponseDto(tokenProvider.createAccessToken(member));
    }
}
