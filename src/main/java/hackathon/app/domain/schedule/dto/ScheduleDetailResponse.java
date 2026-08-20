package hackathon.app.domain.schedule.dto;

import hackathon.app.domain.schedule.entity.Schedule;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemResponse;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 스케줄 상세: 계획 요약(설계서 8개 필드) + 날짜별 작업 + 퍼즐 수.
 * 퍼즐 수 = 유효한 작업(삭제 X, CANCELLED X) 수. CANCELLED 작업은 목록엔 나오지만 수에는 안 들어간다.
 */
public record ScheduleDetailResponse(
        Long id,
        Long categoryId,
        String title,
        ScheduleStatus status,
        LocalDate startDate,
        LocalDate endDate,
        int currentVersion,
        long puzzleCount,
        long completedPuzzleCount,
        List<DayItems> days
) {
    /** 날짜별 작업 묶음. totalCount/completedCount 는 유효한 작업 기준 */
    public record DayItems(LocalDate date, int totalCount, int completedCount, List<ScheduleItemResponse> items) {
    }

    public static ScheduleDetailResponse of(Schedule schedule, List<ScheduleItem> items) {
        Map<LocalDate, List<ScheduleItem>> grouped = new LinkedHashMap<>();
        for (ScheduleItem item : items) {
            grouped.computeIfAbsent(item.getScheduledDate(), d -> new ArrayList<>()).add(item);
        }
        List<DayItems> days = grouped.entrySet().stream()
                .map(e -> new DayItems(
                        e.getKey(),
                        (int) e.getValue().stream().filter(ScheduleItem::countsAsPuzzlePiece).count(),
                        (int) e.getValue().stream().filter(ScheduleItem::isCompleted).count(),
                        e.getValue().stream().map(ScheduleItemResponse::from).toList()))
                .toList();

        long puzzleCount = items.stream().filter(ScheduleItem::countsAsPuzzlePiece).count();
        long completed = items.stream().filter(ScheduleItem::isCompleted).count();

        return new ScheduleDetailResponse(
                schedule.getId(),
                schedule.getCategoryId(),
                schedule.getTitle(),
                schedule.getStatus(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.getCurrentVersion(),
                puzzleCount,
                completed,
                days
        );
    }
}
