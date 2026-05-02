package book_log.demo.repository;

import book_log.demo.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 회원 레포지토리
 *
 * JpaRepository<Member, UUID>를 상속하면 아래 CRUD 메서드가 자동 제공된다.
 *   - save(member)       : 저장/수정
 *   - findById(uuid)     : ID로 단건 조회
 *   - findAll()          : 전체 조회
 *   - deleteById(uuid)   : ID로 삭제
 *   - count()            : 전체 개수
 *
 * 추가 메서드는 Spring Data JPA 네이밍 규칙으로 자동 쿼리 생성.
 * (별도 SQL 작성 불필요)
 */
public interface MemberRepository extends JpaRepository<Member, UUID> {

    /**
     * username으로 회원 단건 조회
     *
     * 로그인 시 아이디로 회원을 찾거나,
     * CustomUserDetailsService에서 Spring Security 인증 처리 시 사용.
     * Optional로 반환해 null 대신 orElseThrow()로 안전하게 처리.
     */
    Optional<Member> findByUsername(String username);

    /**
     * username 중복 여부 확인
     *
     * 회원가입 시 동일 아이디가 이미 존재하는지 체크.
     * SELECT COUNT > 0 쿼리로 실행되어 findByUsername().isPresent()보다 가볍다.
     */
    boolean existsByUsername(String username);
}
