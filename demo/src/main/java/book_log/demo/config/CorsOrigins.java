package book_log.demo.config;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

// APP_CORS_ALLOWED_ORIGINS 값(쉼표 구분)을 CORS 허용 origin 목록으로 바꾼다
final class CorsOrigins {

    // 브라우저 Origin 헤더는 소문자 스킴+호스트(+포트)뿐이다. 경로·쿼리·프래그먼트·사용자정보가 붙은 값은 어떤 요청과도 매치하지 않는다
    private static final Pattern ORIGIN_FORMAT = Pattern.compile("^https?://[^/?#@\\s]+$");

    private CorsOrigins() {
    }

    // 공백 제거, 끝의 '/' 제거(브라우저 Origin 헤더에는 슬래시가 없다), 빈 값 무시. 중복은 하나로 합친다
    // '*'가 하나라도 들어 있으면 예외: 운영에서는 명시적 origin만 허용한다
    // 정리한 값이 http(s)://호스트[:포트] 형식이 아니면 예외(스킴 없음, 경로/쿼리 포함 등). 와일드카드를 먼저 검사해 더 구체적인 메시지를 낸다
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
            if (!ORIGIN_FORMAT.matcher(origin).matches()) {
                throw new IllegalStateException(
                        "app.cors.allowed-origins의 값이 origin 형식(http://호스트[:포트] 또는 https://호스트[:포트], 경로·쿼리 없음)이 아닙니다: " + origin);
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
