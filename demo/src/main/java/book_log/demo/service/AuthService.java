package book_log.demo.service;

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
        if (memberRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUsernameException();
        }

        Member member = Member.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .build();

        try {
            // 동시 가입으로 exists 검사를 통과해도 unique 제약에서 걸리도록 즉시 flush
            return MemberResponseDto.from(memberRepository.saveAndFlush(member));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUsernameException();
        }
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
