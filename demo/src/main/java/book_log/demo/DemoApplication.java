package book_log.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import book_log.demo.config.ApiConfig;

@EnableJpaAuditing
@SpringBootApplication
@EnableConfigurationProperties(ApiConfig.class) // ApiConfig 사용하기 위해 추가
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    // 서버 시작 테스트용
    @Bean
    public CommandLineRunner demo() {
        return (args) -> {
            System.out.println("서버가 정상적으로 시작되었습니다!");
        };
    }
}