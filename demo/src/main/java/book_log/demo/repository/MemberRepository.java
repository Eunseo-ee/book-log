package book_log.demo.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import book_log.demo.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByUsername(String username);

    boolean existsByUsername(String username);
}
