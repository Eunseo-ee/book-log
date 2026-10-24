package book_log.demo.config;

import java.util.Base64;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

// jwt.* 설정(application-api-key.yml). 값이 잘못되면 앱 시작 시 실패한다 (기본값을 두지 않는다)
// 오류 메시지에는 키 이름만 쓰고 값은 절대 넣지 않는다
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties implements InitializingBean {

    // HS256 최소 키 길이(256비트)
    static final int MIN_SECRET_BYTES = 32;

    private String secret; // BASE64
    private Long accessTokenExpiration; // ms
    private Long refreshTokenExpiration; // ms

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret 설정이 없습니다.");
        }
        if (decodeSecret().length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret은 BASE64 디코딩 후 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
        if (accessTokenExpiration == null || accessTokenExpiration <= 0) {
            throw new IllegalStateException("jwt.access-token-expiration은 0보다 큰 값(ms)이어야 합니다.");
        }
        if (refreshTokenExpiration == null || refreshTokenExpiration <= 0) {
            throw new IllegalStateException("jwt.refresh-token-expiration은 0보다 큰 값(ms)이어야 합니다.");
        }
    }

    public byte[] decodeSecret() {
        try {
            return Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            // 원인 예외 메시지에 값이 섞일 수 있어 연결하지 않는다
            throw new IllegalStateException("jwt.secret이 올바른 BASE64 형식이 아닙니다.");
        }
    }
}
