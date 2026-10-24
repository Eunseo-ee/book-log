package book_log.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// toString을 만들지 않는다 (@Data 금지). 비밀번호가 로그에 찍히지 않게
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDto {

    @NotBlank(message = "아이디는 필수입니다.")
    @Size(max = 20, message = "아이디는 20자 이내로 입력해주세요.")
    private String username;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(max = 64, message = "비밀번호는 64자 이내로 입력해주세요.")
    private String password;
}
