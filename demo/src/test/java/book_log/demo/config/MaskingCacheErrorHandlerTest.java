package book_log.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;

class MaskingCacheErrorHandlerTest {

    private static final String QUERY_KEY = "해리포터 비밀검색어:MOVIE:1";

    private Log log;
    private Cache cache;
    private MaskingCacheErrorHandler handler;

    @BeforeEach
    void setUp() {
        log = mock(Log.class);
        when(log.isWarnEnabled()).thenReturn(true);
        cache = mock(Cache.class);
        when(cache.getName()).thenReturn("tmdbSearch");
        handler = new MaskingCacheErrorHandler(log);
    }

    private RuntimeException redisError() {
        return new IllegalStateException("Unable to connect to Redis",
                new RuntimeException("wrapper", new java.net.ConnectException("WRONGPASS invalid username-password pair")));
    }

    private String lastWarn() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(log, times(1)).warn(captor.capture());
        // 예외를 함께 넘기는 warn(Object, Throwable)은 쓰지 않는다(스택트레이스 금지)
        verify(log, never()).warn(any(), any(Throwable.class));
        return String.valueOf(captor.getValue());
    }

    @Test
    void 네_동작_모두_예외를_던지지_않는다() {
        assertThatCode(() -> {
            handler.handleCacheGetError(redisError(), cache, QUERY_KEY);
            handler.handleCachePutError(redisError(), cache, QUERY_KEY, "v");
            handler.handleCacheEvictError(redisError(), cache, QUERY_KEY);
            handler.handleCacheClearError(redisError(), cache);
        }).doesNotThrowAnyException();
        verify(log, times(4)).warn(any(Object.class));
    }

    @Test
    void 로그에_키_원문이_없고_해시_앞8자와_캐시이름이_있다() {
        handler.handleCacheGetError(redisError(), cache, QUERY_KEY);
        String line = lastWarn();

        assertThat(line).doesNotContain("해리포터").doesNotContain("비밀검색어").doesNotContain(QUERY_KEY);
        assertThat(line).contains("'tmdbSearch'").contains("get");
        assertThat(line).contains("key#" + MaskingCacheErrorHandler.hash(QUERY_KEY));
        assertThat(MaskingCacheErrorHandler.hash(QUERY_KEY)).hasSize(8).matches("[0-9a-f]{8}");
    }

    @Test
    void 각_동작_이름이_로그에_드러난다() {
        handler.handleCachePutError(redisError(), cache, QUERY_KEY, "v");
        assertThat(lastWarn()).contains("failed to put entry");
    }

    @Test
    void 예외_클래스와_루트_원인_메시지가_있고_스택트레이스는_없다() {
        handler.handleCacheGetError(redisError(), cache, QUERY_KEY);
        String line = lastWarn();

        assertThat(line).contains("IllegalStateException")
                .contains("java.net.ConnectException")
                .contains("WRONGPASS invalid username-password pair");
        assertThat(line).doesNotContain("Caused by:").doesNotContain("\n").doesNotContain("\tat ").doesNotContain(" at book_log");
    }

    @Test
    void 원인이_없으면_예외_자신의_메시지를_쓰고_개행은_공백으로_바뀐다() {
        handler.handleCacheGetError(new IllegalStateException("line1\nline2\r\n\tat fake.Frame"), cache, "k");
        String line = lastWarn();

        assertThat(line).contains("line1 line2").doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    void 같은_키는_같은_해시_다른_키는_다른_해시() {
        assertThat(MaskingCacheErrorHandler.hash("a:MOVIE:1")).isEqualTo(MaskingCacheErrorHandler.hash("a:MOVIE:1"));
        assertThat(MaskingCacheErrorHandler.hash("a:MOVIE:1")).isNotEqualTo(MaskingCacheErrorHandler.hash("a:MOVIE:2"));
    }

    @Test
    void 키가_null이거나_비어_있어도_터지지_않는다() {
        for (Object key : new Object[] {null, "", "   "}) {
            org.mockito.Mockito.clearInvocations(log);
            assertThatCode(() -> handler.handleCacheGetError(redisError(), cache, key)).doesNotThrowAnyException();
            assertThat(lastWarn()).contains("key#");
        }
    }

    @Test
    void 메시지와_캐시_정보가_null이어도_터지지_않는다() {
        assertThatCode(() -> handler.handleCacheGetError(new RuntimeException((String) null), null, null))
                .doesNotThrowAnyException();
        assertThat(lastWarn()).contains("(no message)");
    }

    @Test
    void 원인_사슬이_순환해도_터지지_않는다() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);

        assertThatCode(() -> handler.handleCacheGetError(a, cache, "k")).doesNotThrowAnyException();
    }

    @Test
    void 아주_긴_메시지는_잘린다() {
        handler.handleCacheGetError(new RuntimeException("x".repeat(5000)), cache, "k");
        assertThat(lastWarn().length()).isLessThan(500);
    }

    @Test
    void WARN이_꺼져_있으면_아무것도_남기지_않는다() {
        when(log.isWarnEnabled()).thenReturn(false);
        handler.handleCacheGetError(redisError(), cache, "k");
        verify(log, never()).warn(any(Object.class));
    }

    @Test
    void 운영_설정은_이_핸들러를_쓴다() {
        assertThat(new CacheErrorConfig().errorHandler()).isInstanceOf(MaskingCacheErrorHandler.class);
    }
}
