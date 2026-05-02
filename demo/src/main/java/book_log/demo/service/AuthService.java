package book_log.demo.service;

import book_log.demo.domain.Member;
import book_log.demo.domain.Role;
import book_log.demo.dto.request.LoginRequestDto;
import book_log.demo.dto.request.SignupRequestDto;
import book_log.demo.dto.response.TokenResponseDto;
import book_log.demo.repository.MemberRepository;
import book_log.demo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 서비스
 *
 * 회원가입, 로그인 비즈니스 로직을 처리한다.
 * - 회원가입: 아이디 중복 확인 → BCrypt 암호화 → DB 저장
 * - 로그인: DB에서 회원 조회 → 비밀번호 검증 → JWT 토큰 발급
 */
@Service
@RequiredArgsConstructor // final 필드를 인수로 받는 생성자 자동 생성 → 생성자 주입 (권장 방식)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;   // BCryptPasswordEncoder (SecurityConfig에서 @Bean으로 등록)
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 회원가입
     *
     * 1. username 중복 여부 확인 (이미 존재하면 예외)
     * 2. 비밀번호를 BCrypt로 암호화
     * 3. Member 엔티티 생성 후 DB에 저장
     * 4. 기본 권한은 ROLE_USER로 설정
     *
     * @param dto 회원가입 요청 정보 (username, password, email)
     * @throws IllegalArgumentException username이 이미 존재할 경우
     */
    @Transactional
    public void register(SignupRequestDto dto) {
        // username 중복 체크: 동일 아이디가 이미 DB에 있으면 예외 발생
        if (memberRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다: " + dto.getUsername());
        }

        // BCrypt로 비밀번호 암호화 후 저장 (평문 저장 절대 금지)
        Member member = Member.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword())) // 단방향 암호화
                .email(dto.getEmail())
                .role(Role.ROLE_USER) // 기본 권한: 일반 사용자
                .build();

        memberRepository.save(member);
    }

    /**
     * 로그인 및 JWT 토큰 발급
     *
     * 1. username으로 DB에서 회원 조회 (없으면 예외)
     * 2. 입력 비밀번호와 저장된 암호화 비밀번호 비교 (BCrypt matches 사용)
     * 3. 검증 통과 시 accessToken(15분), refreshToken(7일) 발급
     *
     * @param dto 로그인 요청 정보 (username, password)
     * @return accessToken + refreshToken 담은 TokenResponseDto
     * @throws IllegalArgumentException 사용자 미존재 또는 비밀번호 불일치 시
     */
    @Transactional(readOnly = true) // DB 읽기만 수행, 불필요한 flush/dirty checking 비활성화
    public TokenResponseDto login(LoginRequestDto dto) {
        // DB에서 username으로 회원 조회
        Member member = memberRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // BCrypt matches(): 입력한 평문 비밀번호와 DB의 암호화 비밀번호 비교
        // encode()는 매번 다른 솔트를 사용하므로 equals() 비교는 불가 → 반드시 matches() 사용
        if (!passwordEncoder.matches(dto.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 인증 성공 → JWT 토큰 발급
        String accessToken = jwtTokenProvider.generateAccessToken(member.getUsername());
        String refreshToken = jwtTokenProvider.generateRefreshToken(member.getUsername());

        return new TokenResponseDto(accessToken, refreshToken);
    }
}
