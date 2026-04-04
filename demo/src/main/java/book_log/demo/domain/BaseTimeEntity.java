package book_log.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass // 공통 매핑 정보가 필요할 때 사용
@EntityListeners(AuditingEntityListener.class) // 엔티티의 변화 감시해서 시간 자동으로 입력
public abstract class BaseTimeEntity {
    
    @CreatedDate // 엔티티가 생성되어 저장될 때 시간 자동 저장
    @Column(updatable = false) // 생성 시간은 수정되면 안 되므로 방어
    private LocalDateTime createdAt;

    @LastModifiedDate // 조회한 엔티티의 값을 변경할 때 시간이 자동 저장
    private LocalDateTime modifiedAt;

}
