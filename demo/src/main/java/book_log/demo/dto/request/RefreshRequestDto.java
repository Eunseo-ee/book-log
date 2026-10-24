package book_log.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// toString을 만들지 않는다 (@Data 금지). 토큰이 로그에 찍히지 않게
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequestDto {

    @NotBlank(message = "refreshToken은 필수입니다.")
    @Size(max = 2048, message = "refreshToken이 너무 깁니다.")
    private String refreshToken;
}
