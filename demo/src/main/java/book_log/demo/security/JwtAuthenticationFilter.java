package book_log.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 인증 필터
 *
 * 모든 HTTP 요청이 들어올 때 한 번씩 실행되는 필터.
 * (OncePerRequestFilter 상속 → 요청당 정확히 1회만 실행 보장)
 *
 * 동작 순서:
 * 1. 요청 헤더에서 "Authorization: Bearer {token}" 추출
 * 2. 토큰이 존재하고 유효하면 Authentication 객체 생성
 * 3. SecurityContext에 Authentication 저장
 *    → 이후 컨트롤러에서 @AuthenticationPrincipal로 현재 사용자 접근 가능
 * 4. 다음 필터로 요청 전달
 *
 * 토큰이 없거나 유효하지 않으면 SecurityContext에 아무것도 저장하지 않고
 * 다음 필터로 넘긴다 → 인증 필요 API 접근 시 401 Unauthorized 응답
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    /** Authorization 헤더 이름 */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer 토큰 접두사 */
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. 요청 헤더에서 JWT 토큰 추출
        String token = resolveToken(request);

        // 2. 토큰이 존재하고 유효한 경우에만 인증 처리
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            // 토큰에서 Authentication 객체 생성 (username + 권한 정보 포함)
            Authentication authentication = jwtTokenProvider.getAuthentication(token);

            // SecurityContext에 저장 → 이후 요청 처리 과정에서 인증 정보 사용 가능
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 3. 다음 필터로 요청 전달 (인증 실패해도 계속 진행 → 접근 제어는 SecurityConfig에서 처리)
        filterChain.doFilter(request, response);
    }

    /**
     * Authorization 헤더에서 토큰 문자열만 추출
     *
     * "Bearer eyJhbGci..." → "eyJhbGci..."
     * 헤더가 없거나 Bearer로 시작하지 않으면 null 반환
     *
     * @param request HTTP 요청
     * @return 토큰 문자열 또는 null
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        // "Bearer " 접두사 제거 후 순수 토큰 문자열만 반환
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
