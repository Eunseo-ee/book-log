package book_log.demo.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

// 빈(@Component)으로 등록하지 않는다. SecurityConfig가 new로 만들어 시큐리티 체인에만 끼운다
// (빈이면 Spring Boot가 서블릿 필터로 또 자동 등록해 체인 밖에서 한 번 더 실행될 수 있다)
//
// 토큰이 없거나 잘못돼도 막지 않고 인증 없이 다음 필터로 넘긴다. 접근 차단은 SecurityConfig의 인가 규칙 몫이다
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            authenticate(request);
        } catch (RuntimeException e) {
            // 어떤 이유로든 인증 단계에서 실패해도 500이 되지 않게 한다. 토큰 값은 남기지 않는다
            SecurityContextHolder.clearContext();
            log.warn("JWT 인증 처리 중 예기치 못한 오류: {}", e.getClass().getSimpleName());
        }
        // 다음 필터/컨트롤러의 예외까지 삼키지 않도록 try 밖에서 호출한다
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return;
        }
        String token = header.substring(BEARER.length()).trim();

        // access 토큰만 인증으로 인정한다. refresh 토큰이면 type 불일치로 empty
        tokenProvider.parse(token, TokenType.ACCESS).ifPresent(claims -> {
            AuthenticatedMember principal = new AuthenticatedMember(claims.userId(), claims.username());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(claims.role().name())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        });
    }
}
