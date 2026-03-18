package book_log.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "api")
@Getter
@Setter
public class ApiConfig {
    private String kakaoKey;
    private String tmdbToken;
    private String anilistUrl = "https://graphql.anilist.co";
}
