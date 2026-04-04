package book_log.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import book_log.demo.dto.response.CalendarResponse;
import book_log.demo.repository.ContentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CalendarService {
    
    private final ContentRepository contentRepository;

    // 특정 월의 활동 기록 날짜 조회
    @Transactional(readOnly = true)
    public CalendarResponse getMonthlyActivity(Long userId, int year, int month) {
        
        // 1. 리포지토리에서 day 리스트 가져오기
        List<Integer> activeDays = contentRepository.findActiveDays(userId, year, month);

        // 2. 숫자 리스트를 DayActivity 객체 리스트로 반환(Java Stream 활용)
        List<CalendarResponse.DayActivity> dayActivities = activeDays.stream()
                .map(day -> new CalendarResponse.DayActivity(day, null, null))
                .toList();

        // 3. 최종 응답 DTO 생성 및 반환
        return new CalendarResponse(year, month, dayActivities);

    }

}
