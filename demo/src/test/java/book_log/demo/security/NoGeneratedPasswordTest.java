package book_log.demo.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;

// Boot가 기본 사용자와 임시 비밀번호를 만들지 않는지 실제 컨텍스트 기동으로 확인한다
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class NoGeneratedPasswordTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("기동 로그에 generated security password가 없고 기본 사용자(inMemory)도 없다")
    void noDefaultUser(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain("Using generated security password");
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).containsExactly("userDetailsService");
        assertThatThrownBy(() -> context.getBean(UserDetailsService.class).loadUserByUsername("user"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
