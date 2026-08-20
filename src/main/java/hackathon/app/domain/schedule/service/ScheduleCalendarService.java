package hackathon.app.domain.schedule.service;

import hackathon.app.domain.schedule.dto.CalendarResponse;
import hackathon.app.domain.schedule.dto.DailyItemResponse;
import hackathon.app.domain.schedule.dto.TodayItemsResponse;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.repository.ScheduleItemRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 날짜 기준 조회: 월별 캘린더, 오늘 할 일. 같은 날짜 안 정렬은 position → priority → id */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleCalendarService {

    private final ScheduleItemRepository scheduleItemRepository;
    private final Clock clock;

    /** GET /calendar?year&month */
    public CalendarResponse getCalendar(Long userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        List<ScheduleItem> items = scheduleItemRepository
                .findByUserIdAndDateBetween(userId, ym.atDay(1), ym.atEndOfMonth());

        Map<LocalDate, List<ScheduleItem>> grouped = new LinkedHashMap<>();
        for (ScheduleItem item : items) {
            grouped.computeIfAbsent(item.getScheduledDate(), d -> new java.util.ArrayList<>()).add(item);
        }
        List<CalendarResponse.Day> days = grouped.entrySet().stream()
                .map(e -> new CalendarResponse.Day(
                        e.getKey(),
                        countValid(e.getValue()),
                        countCompleted(e.getValue()),
                        e.getValue().stream().map(DailyItemResponse::from).toList()))
                .toList();

        return new CalendarResponse(year, month, countValid(items), countCompleted(items), days);
    }

    /** GET /schedule-items/today — 기준 시간대: Asia/Seoul (회원 timezone 도입 시 교체) */
    public TodayItemsResponse getToday(Long userId) {
        LocalDate today = LocalDate.now(clock);
        List<ScheduleItem> items = scheduleItemRepository
                .findByUserIdAndDateBetween(userId, today, today);
        return new TodayItemsResponse(today, countValid(items), countCompleted(items),
                items.stream().map(DailyItemResponse::from).toList());
    }

    /** 전체 수 = 유효한 작업(CANCELLED 제외) 수. CANCELLED 작업은 목록에는 포함되지만 수에는 안 들어간다. */
    private static int countValid(List<ScheduleItem> items) {
        return (int) items.stream().filter(ScheduleItem::countsAsPuzzlePiece).count();
    }

    private static int countCompleted(List<ScheduleItem> items) {
        return (int) items.stream().filter(ScheduleItem::isCompleted).count();
    }
}
