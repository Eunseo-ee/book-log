package book_log.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. CSRF 보안 비활성화 (POST 요청을 허용하기 위해 필수)
            .csrf(AbstractHttpConfigurer::disable)
            
            // 2. 모든 경로에 대해 인증 없이 접근 허용 (개발 단계용)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            )
            
            // 3. H2 콘솔 사용을 위한 프레임 옵션 비활성화
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
            );

        return http.build();
    }
}