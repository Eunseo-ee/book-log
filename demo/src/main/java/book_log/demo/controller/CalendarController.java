package book_log.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import book_log.demo.dto.response.CalendarResponse;
import book_log.demo.service.CalendarService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Validated // 클래스 레벨에 추가하여 파라미터 검증 활성화
public class CalendarController {
    
    private final CalendarService calendarService;

    /**
     * 월별 활동 기록 조회 API
     * GET /api/calendar/activities?year=2026&month=4
     */
    @GetMapping("/activities")
    public ResponseEntity<CalendarResponse> getMonthlyActivity(
        @RequestParam(name = "year") @Min(2000) @Max(2100) int year,
        @RequestParam(name = "month") @Min(1) @Max(12) int month
        // 실제 서비스 시에는 @AuthenticationPrincipal 등을 통해 로그인한 유저 ID 가져오기
    ) {
        // 임시로 유저 ID를 1L로 설정 (로그인 구현 방식 따라 변경 필요)
        Long userId = 1L;

        CalendarResponse response = calendarService.getMonthlyActivity(userId, year, month);

        return ResponseEntity.ok(response);
    }

}
