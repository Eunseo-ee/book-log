package book_log.demo.security;

import book_log.demo.domain.Member;
import book_log.demo.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security의 UserDetailsService 구현체
 *
 * Spring Security가 로그인 처리 또는 JWT 토큰 인증 시
 * username으로 DB에서 사용자를 조회할 때 이 클래스를 호출한다.
 *
 * loadUserByUsername() 반환값(UserDetails)에 담긴 username, password, authorities는
 * Spring Security 내부 인증 로직에서 사용된다.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    /**
     * username으로 DB에서 회원을 조회해 UserDetails 객체로 변환
     *
     * JwtTokenProvider.getAuthentication() 에서 토큰 인증 시 호출됨.
     * 반환된 UserDetails의 getAuthorities()가 권한 체크에 사용된다.
     *
     * @param username 조회할 사용자 아이디
     * @return Spring Security가 사용하는 UserDetails 객체
     * @throws UsernameNotFoundException DB에 해당 username이 없을 경우
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // DB에서 회원 조회, 없으면 Spring Security 표준 예외 발생
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        // Spring Security의 User 객체로 변환
        // - username: 사용자 아이디
        // - password: BCrypt 암호화된 비밀번호 (Spring Security 내부 검증에 사용)
        // - authorities: 권한 목록 (ROLE_USER, ROLE_ADMIN 등)
        return new User(
                member.getUsername(),
                member.getPassword(),
                List.of(new SimpleGrantedAuthority(member.getRole().name()))
        );
    }
}
