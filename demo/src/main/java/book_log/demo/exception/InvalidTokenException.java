package book_log.demo.exception;

// 유효하지 않은 refresh 토큰(위조/만료/type 불일치/회원 없음). 원인은 구분하지 않는다
public class InvalidTokenException extends RuntimeException {
    public static final String MESSAGE = "유효하지 않거나 만료된 토큰입니다. 다시 로그인해주세요.";

    public InvalidTokenException() {
        super(MESSAGE);
    }
}
