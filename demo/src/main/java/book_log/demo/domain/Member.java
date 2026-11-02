package book_log.demo.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "members", // "user"는 PostgreSQL 예약어라 피한다
    uniqueConstraints = @UniqueConstraint(name = Member.EMAIL_UNIQUE_CONSTRAINT, columnNames = "email"))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자
@AllArgsConstructor // 빌더가 사용할 전체 생성자
public class Member extends BaseTimeEntity {

    // 가입 시 어떤 유니크 제약에 걸렸는지 이름으로 구분하기 위해 이름을 고정한다
    public static final String EMAIL_UNIQUE_CONSTRAINT = "uk_members_email";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password; // BCrypt 해시만 저장한다

    @Column(nullable = false) // 저장 전에 trim + 소문자로 정규화한다 (AuthService)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.ROLE_USER;
}
