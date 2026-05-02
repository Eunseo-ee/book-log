package book_log.demo.config;

import book_log.demo.security.JwtAuthenticationFilter;
import book_log.demo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정 클래스
 *
 * JWT 기반 Stateless 인증 구조:
 * - 서버가 세션을 유지하지 않음 (SessionCreationPolicy.STATELESS)
 * - 매 요청마다 Authorization 헤더의 JWT 토큰으로 인증
 * - JwtAuthenticationFilter가 UsernamePasswordAuthenticationFilter 앞에서 토큰 검증
 *
 * 접근 제어:
 * - /api/auth/**       : 인증 없이 접근 가능 (회원가입, 로그인)
 * - /swagger-ui/**     : Swagger UI 접근 허용
 * - /v3/api-docs/**    : OpenAPI 명세 접근 허용
 * - 그 외 모든 요청    : JWT 토큰 인증 필요
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 비밀번호 암호화에 사용할 BCryptPasswordEncoder 빈 등록
     *
     * BCrypt는 단방향 해시 함수로, 같은 비밀번호를 encode()해도 매번 다른 값이 나온다.
     * 따라서 equals()로 비교 불가 → 반드시 passwordEncoder.matches()로 비교해야 한다.
     * AuthService에서 @RequiredArgsConstructor로 자동 주입받아 사용.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * HTTP 보안 설정 (필터 체인 구성)
     *
     * 1. CSRF 비활성화
     *    - REST API는 stateless이고, JWT로 인증하므로 CSRF 토큰이 필요 없음
     *
     * 2. 세션 정책: STATELESS
     *    - 서버가 HttpSession을 생성하거나 사용하지 않음
     *    - 모든 인증 정보는 JWT 토큰에 담겨 클라이언트가 관리
     *
     * 3. URL별 접근 권한 설정
     *    - /api/auth/** : 누구나 접근 가능 (로그인/회원가입은 인증 전에 해야 함)
     *    - 나머지 : 유효한 JWT 토큰 필요
     *
     * 4. JWT 필터 등록
     *    - JwtAuthenticationFilter를 UsernamePasswordAuthenticationFilter 앞에 추가
     *    - 요청이 컨트롤러에 도달하기 전에 토큰 검증 및 SecurityContext 설정
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. CSRF 비활성화 (JWT 기반 REST API에서는 불필요)
            .csrf(AbstractHttpConfigurer::disable)

            // 2. 세션 비활성화 (토큰 기반 Stateless 인증)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 3. URL별 인증 요구 설정
            .authorizeHttpRequests(auth -> auth
                // 인증 없이 접근 허용할 경로
                .requestMatchers(
                    "/api/auth/**",      // 회원가입, 로그인
                    "/swagger-ui/**",    // Swagger UI
                    "/v3/api-docs/**"    // OpenAPI 명세
                ).permitAll()
                // 그 외 모든 요청은 JWT 인증 필요
                .anyRequest().authenticated()
            )

            // 4. JWT 인증 필터를 Spring Security 기본 인증 필터 앞에 등록
            // → 요청이 들어오면 JwtAuthenticationFilter가 먼저 토큰을 검증하고
            //   SecurityContext에 인증 정보를 저장한 뒤 다음 필터로 넘긴다
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
