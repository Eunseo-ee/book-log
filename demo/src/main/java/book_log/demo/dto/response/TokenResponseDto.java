package book_log.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 로그인 성공 시 반환하는 토큰 응답 DTO
 *
 * - accessToken  : 실제 API 호출 시 Authorization 헤더에 담아 보내는 토큰 (만료: 15분)
 * - refreshToken : accessToken 만료 시 재발급 요청에 사용하는 토큰 (만료: 7일)
 *
 * 클라이언트는 accessToken이 만료되면 refreshToken으로 /api/auth/refresh를 호출해
 * 새 accessToken을 받아야 한다. (refresh 엔드포인트는 추후 추가)
 */
@Getter
@AllArgsConstructor // 모든 필드를 받는 생성자 자동 생성 → new TokenResponseDto(access, refresh) 사용
public class TokenResponseDto {

    /** API 호출 시 Authorization: Bearer {accessToken} 형태로 사용 */
    private String accessToken;

    /** accessToken 만료 시 재발급에 사용 (안전한 곳에 보관 권장: HttpOnly 쿠키 등) */
    private String refreshToken;
}
