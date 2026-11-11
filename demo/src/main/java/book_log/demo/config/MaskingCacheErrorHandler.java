package book_log.demo.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * 캐시 오류를 삼키고 WARN 한 줄만 남기는 핸들러.
 * - 스택트레이스는 남기지 않고, 예외 클래스와 루트 원인(클래스: 메시지)만 남긴다.
 * - 캐시 키에는 사용자 검색어가 그대로 들어가므로 원문 대신 SHA-256 앞 8자만 남긴다.
 * - 어떤 입력에서도 예외를 던지지 않는다(핸들러가 터지면 fallback이 깨진다).
 */
public class MaskingCacheErrorHandler implements CacheErrorHandler {

    private static final int HASH_CHARS = 8;
    private static final int MAX_MESSAGE_LENGTH = 200;
    private static final int MAX_CAUSE_DEPTH = 20;

    private final Log logger;

    public MaskingCacheErrorHandler() {
        this(LogFactory.getLog(MaskingCacheErrorHandler.class));
    }

    MaskingCacheErrorHandler(Log logger) {
        this.logger = logger;
    }

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log("get", exception, cache, key, true);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log("put", exception, cache, key, true);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log("evict", exception, cache, key, true);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log("clear", exception, cache, null, false);
    }

    private void log(String action, Throwable exception, Cache cache, Object key, boolean withKey) {
        try {
            if (!logger.isWarnEnabled()) {
                return;
            }
            StringBuilder sb = new StringBuilder("Cache '")
                    .append(cache == null ? "?" : cache.getName())
                    .append("' failed to ").append(action).append(" entry");
            if (withKey) {
                sb.append(" [key#").append(hash(key)).append(']');
            }
            sb.append(": ").append(exception == null ? "?" : exception.getClass().getSimpleName());
            Throwable root = rootOf(exception);
            if (root != null && root != exception) {
                sb.append(", root cause ").append(root.getClass().getName())
                        .append(": ").append(oneLine(root.getMessage()));
            } else if (exception != null) {
                sb.append(": ").append(oneLine(exception.getMessage()));
            }
            logger.warn(sb.toString());
        } catch (RuntimeException ignored) {
            // 로깅 실패가 캐시 fallback을 깨면 안 된다
        }
    }

    static String hash(Object key) {
        try {
            String text = String.valueOf(key);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_CHARS);
        } catch (NoSuchAlgorithmException | RuntimeException e) {
            return "unknown";
        }
    }

    // 원인 사슬의 끝. 순환 참조나 지나치게 깊은 사슬에 대비해 깊이를 제한한다.
    private static Throwable rootOf(Throwable t) {
        Throwable current = t;
        for (int i = 0; current != null && current.getCause() != null
                && current.getCause() != current && i < MAX_CAUSE_DEPTH; i++) {
            current = current.getCause();
        }
        return current;
    }

    // 개행/제어문자를 공백으로 바꿔 한 줄을 유지하고 길이를 제한한다.
    private static String oneLine(String message) {
        if (message == null || message.isBlank()) {
            return "(no message)";
        }
        String flat = message.replaceAll("\\p{Cntrl}+", " ").trim();
        return flat.length() > MAX_MESSAGE_LENGTH ? flat.substring(0, MAX_MESSAGE_LENGTH) + "..." : flat;
    }
}
