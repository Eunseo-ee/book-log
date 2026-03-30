package book_log.demo.controller;

import book_log.demo.domain.Category;
import book_log.demo.domain.Content;
import book_log.demo.dto.response.ContentResponseDto;
import book_log.demo.service.ContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController // JSON 데이터 주고받는 API 컨트롤러임을 선언
@RequestMapping("/api/contents")
@RequiredArgsConstructor
public class ContentController {
    
    private final ContentService contentService;

    /**
     * 콘텐츠 저장 (POST /api/contents)
     * 외부 API에서 가져온 정보나 직접 입력한 정보를 DB에 저장
     */
    @PostMapping
    public ResponseEntity<Long> saveContent(@RequestBody Content content) {
        // @RequestBody : 클라이언트가 보낸 JSON 데이터를 Content 객체로 변환
        Long savedId = contentService.saveContent(content);

        // 생성 성공 시 201 Created 상태 코드와 저장된 리소스의 경로 반환
        return ResponseEntity.created(URI.create("/api/contents/" + savedId)).body(savedId);
    }

    /**
     * 특정 콘텐츠 저장 여부 확인 (GET /api/contents/check)
     * externalId와 category를 통해 이미 우리 DB에 있는지 확인
     */
    @GetMapping("/check")
    public ResponseEntity<Boolean> checkDuplicate (
            @RequestParam String externalId,
            @RequestParam Category category) {

            // 이미 저장되어 있다면 true, 아니면 false 반환
            boolean isSaved = contentService.isAlreadySaved(externalId, category);
            return ResponseEntity.ok(isSaved);
        }

    @GetMapping("/filter")
    public ResponseEntity<List<ContentResponseDto>> getFilteredContents(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Category category) {

                List<ContentResponseDto> response = contentService.getFilteredContents(year, category);

                return ResponseEntity.ok(response);
            }

}
