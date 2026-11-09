package book_log.demo.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.util.TestPropertyValues.Type;

import com.zaxxer.hikari.HikariDataSource;

// 25-f: 운영 Cloud Run YAML에 쓸 Hikari 풀 크기 환경변수 이름이 실제 풀에 적용되는지 확인한다.
// 환경변수 소스를 systemEnvironment 종류로 넣는 방식은 EnvironmentBindingTest(25-a)와 같다. 임베디드 H2만 쓰고 외부 DB에는 접속하지 않는다
class HikariEnvBindingTest {

    @Test
    @DisplayName("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5 가 실제 HikariDataSource의 최대 풀 크기 5가 된다")
    void maximumPoolSizeFromEnvironmentVariable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withInitializer(ctx -> TestPropertyValues.of(
                        "SPRING_DATASOURCE_URL=jdbc:h2:mem:hikari-env-test",
                        "SPRING_DATASOURCE_USERNAME=sa",
                        "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5")
                        .applyTo(ctx.getEnvironment(), Type.SYSTEM_ENVIRONMENT))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    HikariDataSource ds = ctx.getBean(HikariDataSource.class);
                    assertThat(ds.getMaximumPoolSize()).isEqualTo(5);
                });
    }
}
