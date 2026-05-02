package book_log.demo.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 회원 엔티티
 *
 * BaseTimeEntity를 상속받아 createdAt, modifiedAt을 자동으로 관리.
 * id는 UUID 타입 사용 → DB에 종속되지 않고 분산 환경에서도 충돌 없이 고유 ID 생성.
 * password는 BCrypt로 암호화된 값만 저장하고, 절대 평문을 저장하지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 기본 생성자 필요, 외부에서 직접 new Member() 방지
@Table(name = "members")
public class Member extends BaseTimeEntity {

    /**
     * 기본키: UUID
     * - IDENTITY(auto_increment) 대신 UUID를 쓰면 애플리케이션 레벨에서 ID 생성 가능
     * - updatable = false: 한 번 생성된 ID는 절대 변경 불가
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * 로그인 아이디 (고유값)
     * - unique = true로 중복 가입 방지
     */
    @Column(nullable = false, unique = true)
    private String username;

    /**
     * BCrypt로 암호화된 비밀번호
     * - AuthService에서 passwordEncoder.encode(rawPassword) 후 이 필드에 저장
     * - 평문 비밀번호는 절대 저장하지 않음
     */
    @Column(nullable = false)
    private String password;

    /**
     * 이메일
     */
    @Column(nullable = false)
    private String email;

    /**
     * 권한 (ROLE_USER / ROLE_ADMIN)
     * - EnumType.STRING: 숫자 대신 문자열로 저장 → enum 순서 변경에 안전
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * @Builder를 이용한 생성 패턴
     * - Member.builder().username("...").password("...").email("...").role(Role.ROLE_USER).build()
     * - 생성자 인수 순서 실수 방지, 가독성 향상
     */
    @Builder
    public Member(String username, String password, String email, Role role) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
    }
}
