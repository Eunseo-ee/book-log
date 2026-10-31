package book_log.demo.exception;

// 다른 회원 소유의 리소스에 접근함 (403)
public class ForbiddenResourceException extends RuntimeException {
    public static final String MESSAGE = "해당 리소스에 대한 권한이 없습니다.";

    public ForbiddenResourceException() {
        super(MESSAGE);
    }
}
