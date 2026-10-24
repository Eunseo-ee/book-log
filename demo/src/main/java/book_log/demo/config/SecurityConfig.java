package book_log.demo.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 허용할 프론트 주소. 여러 개면 쉼표로 구분 (배포 시 이 값만 바꾼다)
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. CSRF 보안 비활성화 (POST 요청을 허용하기 위해 필수)
            .csrf(AbstractHttpConfigurer::disable)

            // 2. CORS 활성화. 아래 corsConfigurationSource 빈을 시큐리티 필터 단계에서 적용한다 (preflight 포함)
            .cors(Customizer.withDefaults())

            // 3. 모든 경로에 대해 인증 없이 접근 허용 (개발 단계용)
            // TODO: task 19에서 authenticated 전환
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )

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

    // 전역 CORS 설정. 컨트롤러별 @CrossOrigin은 쓰지 않는다
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
