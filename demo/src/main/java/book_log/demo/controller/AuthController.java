package book_log.demo.controller;

import book_log.demo.dto.request.LoginRequestDto;
import book_log.demo.dto.request.SignupRequestDto;
import book_log.demo.dto.response.TokenResponseDto;
import book_log.demo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 관련 API 컨트롤러
 *
 * 모든 엔드포인트는 SecurityConfig에서 permitAll()로 설정되어 있어 인증 없이 접근 가능.
 * - POST /api/auth/register : 회원가입
 * - POST /api/auth/login    : 로그인 (JWT 토큰 발급)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 회원가입 API
     *
     * 요청 예시:
     * POST /api/auth/register
     * {
     *   "username": "myuser",
     *   "password": "password123",
     *   "email": "user@example.com"
     * }
     *
     * @param dto 회원가입 요청 정보 (@Valid로 유효성 검사 자동 수행)
     * @return 200 OK + 성공 메시지
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody SignupRequestDto dto) {
        authService.register(dto);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    /**
     * 로그인 API
     *
     * 요청 예시:
     * POST /api/auth/login
     * {
     *   "username": "myuser",
     *   "password": "password123"
     * }
     *
     * 응답 예시:
     * {
     *   "accessToken": "eyJhbGci...",   // 15분 유효
     *   "refreshToken": "eyJhbGci..."   // 7일 유효
     * }
     *
     * 이후 인증이 필요한 API 호출 시 요청 헤더에 아래처럼 포함:
     * Authorization: Bearer {accessToken}
     *
     * @param dto 로그인 요청 정보 (@Valid로 유효성 검사)
     * @return 200 OK + accessToken/refreshToken
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto dto) {
        TokenResponseDto tokenResponse = authService.login(dto);
        return ResponseEntity.ok(tokenResponse);
    }
}
