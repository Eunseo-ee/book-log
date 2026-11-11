package book_log.demo.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import book_log.demo.security.JsonAccessDeniedHandler;
import book_log.demo.security.JsonAuthenticationEntryPoint;
import book_log.demo.security.JwtAuthenticationFilter;
import book_log.demo.security.JwtTokenProvider;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 로컬 개발용 기본 origin. prod 프로파일에서는 쓰지 않는다
    static final String DEV_DEFAULT_ORIGIN = "http://localhost:3000";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider,
            ObjectMapper objectMapper, Environment env) throws Exception {
        http
            // 1. CSRF 보안 비활성화 (POST 요청을 허용하기 위해 필수)
            .csrf(AbstractHttpConfigurer::disable)

            // 2. CORS 활성화. 아래 corsConfigurationSource 빈을 시큐리티 필터 단계에서 적용한다 (preflight 포함)
            .cors(Customizer.withDefaults())

            // 세션을 만들지 않는다 (인증 정보는 매 요청의 JWT로만 전달)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 3. 로그인/가입/재발급, Swagger, actuator health만 공개하고 나머지는 인증 필요
            // CORS preflight(OPTIONS)는 위 cors()가 등록한 CorsFilter가 인가 검사보다 먼저 응답한다
            // prod에서는 springdoc 자체가 꺼져 있으므로(ops-prod.properties) Swagger 경로를 열어 두지 않는다.
            // 열어 두면 핸들러 없는 경로가 인증 없이 DispatcherServlet까지 가 catch-all 500이 된다. 닫으면 401
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/auth/**").permitAll();
                if (!env.acceptsProfiles(Profiles.of("prod"))) {
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                }
                auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                auth.anyRequest().authenticated();
            })

            // 401/403도 GlobalExceptionHandler와 같은 ErrorResponse JSON으로 통일한다
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
                .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper))
            )

            // JWT 필터는 빈이 아니라 여기서 new로 만든다 (빈이면 서블릿 필터로 한 번 더 자동 등록됨)
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)

            // 4. H2 콘솔 사용을 위한 프레임 옵션 비활성화
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
            );

        return http.build();
    }

    // 비밀번호는 BCrypt 해시로만 저장한다
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 인증은 JWT 필터와 AuthService(DB 직접 조회)로만 하므로 UserDetailsService를 쓰지 않는다.
    // 빈이 없으면 Boot가 기본 사용자 user와 임시 비밀번호를 만들어 로그에 찍으므로, 아무도 인증하지 못하는 빈을 둬 막는다
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("UserDetailsService 인증은 사용하지 않습니다.");
        };
    }

    // 전역 CORS 설정. 컨트롤러별 @CrossOrigin은 쓰지 않는다
    // 허용 origin은 app.cors.allowed-origins(환경변수 APP_CORS_ALLOWED_ORIGINS), 여러 개면 쉼표로 구분
    // 인증은 Authorization 헤더라 쿠키가 필요 없어 allowCredentials는 설정하지 않는다(false)
    @Bean
    public CorsConfigurationSource corsConfigurationSource(Environment env) {
        List<String> origins = CorsOrigins.parse(env.getProperty("app.cors.allowed-origins"));
        if (origins.isEmpty()) {
            // 운영에서 값이 빠진 채 localhost만 허용하는 상태로 조용히 뜨지 않게 기동을 막는다
            if (env.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(
                        "prod 프로파일에서는 app.cors.allowed-origins(APP_CORS_ALLOWED_ORIGINS)가 필요합니다.");
            }
            origins = List.of(DEV_DEFAULT_ORIGIN);
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
