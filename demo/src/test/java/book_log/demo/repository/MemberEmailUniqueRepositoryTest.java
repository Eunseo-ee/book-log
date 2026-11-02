package book_log.demo.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import book_log.demo.domain.Member;

// members.email unique 제약이 DB에서 실제로 걸리는지 확인한다.
// 기본 Replace.ANY라서 임베디드 H2로 돌고(실제 DB에 붙지 않음), 스키마는 엔티티 매핑으로 생성된다
@DataJpaTest
class MemberEmailUniqueRepositoryTest {

    @Configuration
    @EnableJpaAuditing
    @EntityScan(basePackages = "book_log.demo.domain")
    @EnableJpaRepositories(basePackages = "book_log.demo.repository")
    static class TestConfig {
    }

    @Autowired
    private MemberRepository memberRepository;

    private Member member(String username, String email) {
        return Member.builder().username(username).password("x").email(email).build();
    }

    @Test
    @DisplayName("아이디가 달라도 같은 이메일은 DB가 거부하고, 위반한 제약 이름이 이메일 제약이다")
    void sameEmailIsRejectedByDatabase() {
        memberRepository.saveAndFlush(member("alice", "same@example.com"));

        assertThatThrownBy(() -> memberRepository.saveAndFlush(member("bob", "same@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> {
                    ConstraintViolationException cve = null;
                    for (Throwable t = e; t != null; t = t.getCause()) {
                        if (t instanceof ConstraintViolationException c) {
                            cve = c;
                            break;
                        }
                    }
                    assertThat(cve).isNotNull();
                    assertThat(cve.getConstraintName()).isNotNull();
                    assertThat(cve.getConstraintName().toLowerCase()).contains(Member.EMAIL_UNIQUE_CONSTRAINT);
                });
    }

    @Test
    @DisplayName("서로 다른 이메일은 저장되고 existsByEmail로 조회된다")
    void differentEmailsAreSaved() {
        memberRepository.saveAndFlush(member("alice", "a@example.com"));
        memberRepository.saveAndFlush(member("bob", "b@example.com"));

        assertThat(memberRepository.existsByEmail("a@example.com")).isTrue();
        assertThat(memberRepository.existsByEmail("c@example.com")).isFalse();
    }
}
