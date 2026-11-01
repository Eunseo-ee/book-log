package book_log.demo.security;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// 컨트롤러가 @AuthenticationPrincipal AuthenticatedMember로 받는 로그인 사용자를 테스트 요청에 심는다
// (@WithMockUser는 principal이 User라 AuthenticatedMember로 변환되지 않아 쓰지 않는다)
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor member(long id) {
        return member(id, "user" + id, "ROLE_USER");
    }

    public static RequestPostProcessor member(long id, String username, String role) {
        return SecurityMockMvcRequestPostProcessors.authentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(id, username), null, List.of(new SimpleGrantedAuthority(role))));
    }
}
