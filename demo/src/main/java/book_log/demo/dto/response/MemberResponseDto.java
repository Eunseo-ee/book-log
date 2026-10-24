package book_log.demo.dto.response;

import book_log.demo.domain.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;

// 비밀번호(해시 포함)는 절대 담지 않는다
@Getter
@AllArgsConstructor
public class MemberResponseDto {
    private Long id;
    private String username;

    public static MemberResponseDto from(Member member) {
        return new MemberResponseDto(member.getId(), member.getUsername());
    }
}
