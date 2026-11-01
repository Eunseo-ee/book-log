package book_log.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import book_log.demo.dto.response.CalendarResponse;
import book_log.demo.security.AuthenticatedMember;
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
        @RequestParam(name = "month") @Min(1) @Max(12) int month,
        @AuthenticationPrincipal AuthenticatedMember member
    ) {
        CalendarResponse response = calendarService.getMonthlyActivity(member.id(), year, month);

        return ResponseEntity.ok(response);
    }

}
