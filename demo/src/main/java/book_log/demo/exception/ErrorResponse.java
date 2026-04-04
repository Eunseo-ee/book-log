package book_log.demo.exception;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private int status; // HTTP 상태 코드 (ex. 400, 404)
    private String message; // 에러 메시지
}
