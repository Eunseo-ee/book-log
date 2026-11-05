package book_log.demo.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
// 키가 비어 있으면 앱 시작 시 바인딩 단계에서 실패한다 (기본값을 두지 않는다)
@ConfigurationProperties(prefix = "api")
@Validated
@Getter
@Setter
public class ApiConfig {
    @NotBlank
    private String kakaoKey;
    @NotBlank
    private String tmdbToken;
    // private String anilistUrl = "https://graphql.anilist.co";

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(3))
                .build();
    }
}
