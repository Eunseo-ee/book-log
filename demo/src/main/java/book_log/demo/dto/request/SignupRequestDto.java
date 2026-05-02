package book_log.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원가입 요청 DTO
 *
 * POST /api/auth/register 호출 시 클라이언트가 Body로 전달하는 데이터.
 * 컨트롤러에서 @Valid와 함께 사용하면 아래 검증 조건을 자동으로 적용.
 * 검증 실패 시 MethodArgumentNotValidException 발생 → GlobalExceptionHandler에서 처리 가능.
 */
@Getter
@NoArgsConstructor // Jackson이 JSON → 객체 변환 시 기본 생성자 필요
public class SignupRequestDto {

    /**
     * 로그인 아이디
     * - 공백 불가 (@NotBlank)
     * - 4~20자 제한 (@Size)
     */
    @NotBlank(message = "사용자 아이디는 필수입니다.")
    @Size(min = 4, max = 20, message = "아이디는 4자 이상 20자 이하로 입력해주세요.")
    private String username;

    /**
     * 비밀번호 (평문)
     * - 서비스 계층에서 BCrypt로 암호화 후 저장
     * - 8자 이상 강제
     */
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;

    /**
     * 이메일
     * - @Email로 형식 검증 (xxx@xxx.xxx 패턴 확인)
     */
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;
}
