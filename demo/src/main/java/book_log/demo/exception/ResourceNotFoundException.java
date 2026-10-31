package book_log.demo.exception;

// 요청한 id의 리소스가 없음 (404)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
