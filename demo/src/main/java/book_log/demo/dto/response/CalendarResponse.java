package book_log.demo.dto.response;

import java.util.List;

public record CalendarResponse(
    int year,
    int month,
    List<DayActivity> days
) {
    /**
     * 날짜별 활동 정보를 담는 레코드
     * 지금은 day만 사용하지만, 나중에 thumbnail, contentId 등을 추가하여 확장
     */
    public record DayActivity(
        int day, // 기록이 있는 날짜 (점을 찍어야 할 날짜들의 목록)
        String thumbnail, // 추후 메인 기능용 (현재는 null)
        Long count // 해당 날짜에 쓴 글 개수
    ) {
        // 사이드바 점 찍기용 생성자
        public static DayActivity of(int day) {
            return new DayActivity(day, null, null);
        }
    }
}
