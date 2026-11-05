package book_log.demo.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    // 서비스의 "오늘"은 서버(JVM) 시간대가 아니라 한국 기준으로 고정한다.
    // 테스트에서는 이 빈 대신 Clock.fixed를 주입해 경계 시각을 재현한다.
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SERVICE_ZONE);
    }
}
