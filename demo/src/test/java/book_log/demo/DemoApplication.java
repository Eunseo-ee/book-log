package book_log.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    // 서버가 시작될 때 실행되는 코드
    @Bean
    public CommandLineRunner demo() {
        return (args) -> {
            System.out.println("데이터 저장 테스트 시작!");
            // 여기에 나중에 Repository를 주입받아 save하는 코드를 넣을 수 있습니다.
        };
    }
}