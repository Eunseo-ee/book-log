package book_log.demo.dto.response;

import book_log.demo.domain.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 비밀번호(해시 포함)는 절대 담지 않는다. toString도 만들지 않아 토큰이 로그에 찍히지 않는다
@Getter
@AllArgsConstructor
public class LoginResponseDto {
    private Long id;
    private String username;
    private String accessToken;
    private String refreshToken;

    public static LoginResponseDto of(Member member, String accessToken, String refreshToken) {
        return new LoginResponseDto(member.getId(), member.getUsername(), accessToken, refreshToken);
    }
}
