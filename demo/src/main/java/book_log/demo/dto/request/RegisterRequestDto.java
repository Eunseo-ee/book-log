package book_log.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import book_log.demo.validation.MaxUtf8Bytes;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// toString을 만들지 않는다 (@Data 금지). 비밀번호가 로그에 찍히지 않게
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDto {

    @NotBlank(message = "아이디는 필수입니다.")
    @Size(min = 4, max = 20, message = "아이디는 4~20자여야 합니다.")
    private String username;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
    // BCrypt는 72바이트까지만 해시에 반영한다. 한글은 한 글자가 3바이트라 문자 수 검증만으로는 부족하다
    @MaxUtf8Bytes(value = 72, message = "비밀번호가 너무 깁니다. 72바이트 이하여야 합니다 (영문·숫자·기호는 1글자 1바이트, 한글은 1글자 3바이트라 24자까지).")
    private String password;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자 이내로 입력해주세요.")
    private String email;
}
