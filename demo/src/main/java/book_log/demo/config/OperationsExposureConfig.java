package book_log.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;

/**
 * 운영에서 바깥에 드러나는 면(actuator, 에러 본문, Swagger)의 설정을 코드와 함께 배포되게 하는 클래스.
 *
 * application*.yml은 gitignore + 이미지 제외 대상이라(25-a, 25-b) 거기에 두면 운영에서 사라진다.
 * 그래서 application으로 시작하지 않는 properties 파일을 @PropertySource로 읽는다.
 * @PropertySource는 우선순위가 가장 낮아 환경변수나 로컬 application.yml이 덮어쓸 수 있다.
 */
@Configuration
@PropertySource("classpath:ops-defaults.properties")
public class OperationsExposureConfig {

    @Configuration
    @Profile("prod")
    @PropertySource("classpath:ops-prod.properties")
    static class ProdOnly {
    }
}
