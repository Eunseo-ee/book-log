package book_log.demo.config;

import java.util.Arrays;
import java.util.List;

// APP_CORS_ALLOWED_ORIGINS 값(쉼표 구분)을 CORS 허용 origin 목록으로 바꾼다
final class CorsOrigins {

    private CorsOrigins() {
    }

    // 공백 제거, 끝의 '/' 제거(브라우저 Origin 헤더에는 슬래시가 없다), 빈 값 무시. 중복은 하나로 합친다
    // '*'가 하나라도 들어 있으면 예외: 운영에서는 명시적 origin만 허용한다
    static List<String> parse(String raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> origins = Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(CorsOrigins::stripTrailingSlashes)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        for (String origin : origins) {
            if (origin.contains("*")) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins에는 와일드카드(*)를 쓸 수 없습니다. 허용할 origin을 명시하세요: " + origin);
            }
        }
        return origins;
    }

    private static String stripTrailingSlashes(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') {
            end--;
        }
        return s.substring(0, end);
    }
}
