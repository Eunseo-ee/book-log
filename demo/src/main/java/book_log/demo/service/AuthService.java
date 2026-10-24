package book_log.demo.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import book_log.demo.domain.Member;
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
    // 없는 아이디도 BCrypt 비교를 한 번 수행해 응답 시간 차이로 아이디 존재 여부가 드러나지 않게 한다
    private final String dummyHash;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
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
    public MemberResponseDto login(LoginRequestDto request) {
        Member member = memberRepository.findByUsername(request.getUsername()).orElse(null);

        if (member == null) {
            passwordEncoder.matches(request.getPassword(), dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new InvalidCredentialsException();
        }
        return MemberResponseDto.from(member);
    }
}
