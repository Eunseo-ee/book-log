package book_log.demo.controller;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import book_log.demo.dto.response.StatisticsResponse;
import book_log.demo.service.StatisticsService;
import lombok.RequiredArgsConstructor;

@CrossOrigin(origins = "*") // 테스트 단계에서는 모두 허용, 나중에 리액트 주소만 허용
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {
    
    private final StatisticsService statisticsService;

    // 월별 통계 데이터 조회 API
    // GET /api/statistics?year=2026&month=4

    @GetMapping
    public ResponseEntity<StatisticsResponse> getMonthlyStats(
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month) {
                
                // 간단한 파라미터 검증 (선택 사항)
                if (month < 1 || month > 12) {
                    return ResponseEntity.badRequest().build();
                }

                // 값이 없으면 현재 날짜 기준
                int targetYear = (year != null) ? year : LocalDate.now().getYear();
                int targetMonth = (month != null) ? month : LocalDate.now().getMonthValue();
                
                StatisticsResponse response = statisticsService.getMonthlyStatistics(targetYear, targetMonth);
                return ResponseEntity.ok(response);
            }

}
