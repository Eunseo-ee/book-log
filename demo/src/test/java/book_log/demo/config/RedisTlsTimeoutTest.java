package book_log.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 25-f: ssl.enabled=true(Upstash 같은 TLS Redis)인데 서버가 TCP 연결만 받고 TLS 핸드셰이크에 응답하지 않을 때,
 * 운영 기본 timeout(ops-defaults.properties의 1초)이 실제로 동작하는지 본다. 로컬 ServerSocket만 쓰고 외부에 접속하지 않는다.
 */
@Timeout(60)
class RedisTlsTimeoutTest {

    private final List<Socket> accepted = new CopyOnWriteArrayList<>();
    private ServerSocket silentServer;

    @AfterEach
    void closeSockets() throws IOException {
        for (Socket s : accepted) {
            s.close();
        }
        if (silentServer != null) {
            silentServer.close();
        }
    }

    @Test
    @DisplayName("TLS 켠 상태에서 핸드셰이크에 무응답인 서버: 명령이 timeout 근처에서 예외로 끝난다 (무한 대기 아님)")
    void tlsHandshakeStall_failsFast() throws IOException {
        int port = startSilentServer();

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
                .withUserConfiguration(OperationsExposureConfig.class)
                .withPropertyValues("spring.data.redis.host=127.0.0.1",
                        "spring.data.redis.port=" + port,
                        "spring.data.redis.ssl.enabled=true")
                .run(ctx -> {
                    StringRedisTemplate redis = ctx.getBean(StringRedisTemplate.class);

                    long start = System.nanoTime();
                    // 15초 상한: Lettuce 기본값(명령 60초/연결 10초)이면 이 상한을 넘겨 실패한다
                    Throwable thrown = assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                        try {
                            redis.opsForValue().get("any-key");
                            return null;
                        } catch (Throwable t) {
                            return t;
                        }
                    });
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

                    assertThat(accepted).as("TCP 연결은 실제로 맺어졌어야 한다(TLS 단계에서 멈춘 상황)").isNotEmpty();
                    assertThat(thrown).as("무응답 TLS 서버에서는 예외로 끝나야 한다 (CacheErrorConfig가 삼키는 대상)").isNotNull();
                    assertThat(elapsedMs).as("TLS 무응답 시 소요 시간(ms)").isLessThan(8_000);
                });
    }

    private int startSilentServer() throws IOException {
        silentServer = new ServerSocket(0);
        Thread acceptor = new Thread(() -> {
            while (!silentServer.isClosed()) {
                try {
                    accepted.add(silentServer.accept());
                } catch (IOException e) {
                    return;
                }
            }
        }, "silent-tls-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        return silentServer.getLocalPort();
    }
}
