package book_log.demo.exception;

import java.time.DateTimeException;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice // 전역 에러 처리기 - 모든 컨트롤러에서 발생하는 예외 잡음
public class GlobalExceptionHandler {
    
    // 1. 중복 데이터 예외 처리 (IllegalStateException)
    // 특정 예외 IllegalStateException 발생했을 떄 호출될 메서드 지정
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException e) {
        ErrorResponse response = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 1-1. 회원가입 아이디 중복 (409)
    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUsername(DuplicateUsernameException e) {
        ErrorResponse response = new ErrorResponse(HttpStatus.CONFLICT.value(), e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    // 1-1-1. 회원가입 이메일 중복 (409)
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException e) {
        ErrorResponse response = new ErrorResponse(HttpStatus.CONFLICT.value(), e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    // 1-2. 로그인 실패 (아이디 없음/비밀번호 틀림 모두 같은 상태 코드와 메시지)
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        ErrorResponse response = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    // 1-3. 유효하지 않은 refresh 토큰 (위조/만료/type 불일치/회원 없음 모두 같은 응답)
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException e) {
        ErrorResponse response = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    // 1-4. 다른 회원의 리소스 접근 (403)
    @ExceptionHandler(ForbiddenResourceException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenResource(ForbiddenResourceException e) {
        ErrorResponse response = new ErrorResponse(HttpStatus.FORBIDDEN.value(), e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // 1-5. 없는 리소스 (404)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException e) {
        ErrorResponse response = new ErrorResponse(HttpStatus.NOT_FOUND.value(), e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // 2. 잘못된 인자 예외 처리 (IllegalArgumentException - 평점 오류 등)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        ErrorResponse response = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 3. 날짜 형식이 잘못되었을 때 (13월 입력 등)
    @ExceptionHandler(DateTimeException.class)
    public ResponseEntity<ErrorResponse> handleDateTimeException(DateTimeException e) {
        ErrorResponse response = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "날짜 형식이 올바르지 않습니다. 월은 1-12 사이여야 합니다."
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 3-1. 클래스 @Validated의 @Min/@Max 위반 (캘린더 year/month 등). 핸들러가 없으면 500으로 샌다
    // 메시지는 위반 항목의 필드명 + 제약 메시지만 쓴다 (입력값은 에코하지 않는다)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> lastNode(v.getPropertyPath().toString()) + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return badRequest(message);
    }

    // 3-2. 쿼리 파라미터 타입 변환 실패 (Enum 오타, 숫자 아님 등). 입력값은 메시지에 넣지 않는다
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return badRequest("파라미터 '" + e.getName() + "' 값이 올바르지 않습니다.");
    }

    // 3-3. 필수 쿼리 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return badRequest("필수 파라미터 '" + e.getParameterName() + "'가 없습니다.");
    }

    // 3-4. 깨진 JSON / 변환 불가 body. 파서 메시지에는 입력 조각이 들어 있어 고정 문구만 쓴다
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return badRequest("요청 본문을 읽을 수 없습니다. JSON 형식을 확인해주세요.");
    }

    private static ResponseEntity<ErrorResponse> badRequest(String message) {
        return new ResponseEntity<>(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message), HttpStatus.BAD_REQUEST);
    }

    // "getMonthlyActivity.month" → "month"
    private static String lastNode(String propertyPath) {
        return propertyPath.substring(propertyPath.lastIndexOf('.') + 1);
    }

    // 4. @Valid 유효성 검사 실패 시 발생하는 예외 처리
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<java.util.Map<String, String>> handleValidationExceptions(org.springframework.web.bind.MethodArgumentNotValidException ex) {

        java.util.Map<String, String> errors = new java.util.HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        return ResponseEntity.badRequest().body(errors);
    }

    // 5. 서버 내부의 모든 알 수 없는 에러 (최후의 보루)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception e) {
        e.printStackTrace(); // 서버 로그 확인용
        ErrorResponse response = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
