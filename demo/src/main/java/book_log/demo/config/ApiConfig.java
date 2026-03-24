package book_log.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "api")
@Getter
@Setter
public class ApiConfig {
    private String kakaoKey;
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
