package book_log.demo.security;

// 인증된 요청의 principal. 컨트롤러에서 @AuthenticationPrincipal AuthenticatedMember로 받는다
public record AuthenticatedMember(Long id, String username) {
}
