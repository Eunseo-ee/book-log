package book_log.demo.exception;

// 아이디 없음/비밀번호 틀림을 구분하지 않는 단일 로그인 실패 예외 (아이디 열거 방지)
public class InvalidCredentialsException extends RuntimeException {
    public static final String MESSAGE = "아이디 또는 비밀번호가 올바르지 않습니다.";

    public InvalidCredentialsException() {
        super(MESSAGE);
    }
}
