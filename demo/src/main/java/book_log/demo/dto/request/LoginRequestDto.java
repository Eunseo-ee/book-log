package book_log.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 요청 DTO
 *
 * POST /api/auth/login 호출 시 클라이언트가 Body로 전달하는 데이터.
 * username + password 조합으로 인증하고, 성공 시 JWT 토큰을 반환.
 */
@Getter
@NoArgsConstructor // Jackson JSON 역직렬화를 위한 기본 생성자
public class LoginRequestDto {

    /**
     * 로그인 아이디
     */
    @NotBlank(message = "사용자 아이디는 필수입니다.")
    private String username;

    /**
     * 비밀번호 (평문)
     * - 서비스 계층에서 BCrypt matches()로 저장된 암호화 값과 비교
     */
    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}
