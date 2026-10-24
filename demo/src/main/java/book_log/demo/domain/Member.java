package book_log.demo.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "members") // "user"는 PostgreSQL 예약어라 피한다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자
@AllArgsConstructor // 빌더가 사용할 전체 생성자
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password; // BCrypt 해시만 저장한다

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.ROLE_USER;
}
