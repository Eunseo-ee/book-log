package book_log.demo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import book_log.demo.dto.request.LoginRequestDto;
import book_log.demo.dto.request.RefreshRequestDto;
import book_log.demo.dto.request.RegisterRequestDto;
import book_log.demo.dto.response.AccessTokenResponseDto;
import book_log.demo.dto.response.LoginResponseDto;
import book_log.demo.dto.response.MemberResponseDto;
import book_log.demo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<MemberResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // 로그인 성공 시 id/username과 access·refresh 토큰을 응답한다
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // refresh 토큰으로 새 access 토큰만 발급한다
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponseDto> refresh(@Valid @RequestBody RefreshRequestDto request) {
        return ResponseEntity.ok(authService.refresh(request));
    }
}
