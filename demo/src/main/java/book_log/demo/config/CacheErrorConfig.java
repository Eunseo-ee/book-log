package book_log.demo.config;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * 캐시(Redis) 장애가 검색 장애로 번지지 않게 하는 설정.
 *
 * 기본 동작: 캐시 읽기/쓰기에서 예외가 나면 @Cacheable 메서드까지 예외가 전파되어 검색이 실패한다.
 * 이 설정: 캐시 예외를 WARN 로그로 남기고 삼킨다 → 캐시 미스처럼 취급되어 외부 API를 직접 호출한다.
 *
 * 캐시는 '성능 최적화'일 뿐 '필수 의존성'이 아니라는 원칙을 코드로 표현한 것.
 * (CacheManager 설정은 RedisCacheConfig에 두고, 이 클래스는 에러 핸들러만 담당한다.)
 */
@Configuration
public class CacheErrorConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        // 스프링 제공 구현체: get/put/evict/clear 실패를 로그만 남기고 무시
        return new LoggingCacheErrorHandler();
    }
}
