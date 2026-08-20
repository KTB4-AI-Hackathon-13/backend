package hackathon.app.ranking.service;

import hackathon.app.ranking.enums.PeriodType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** 랭킹 기준일을 포함하는 캘린더 기간. ALL은 시작일이 null이다. */
public record RankingPeriodWindow(LocalDate from, LocalDate to) {

    public static RankingPeriodWindow of(PeriodType period, LocalDate rankingDate) {
        LocalDate from = switch (period) {
            case DAILY -> rankingDate;
            case WEEKLY -> rankingDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> rankingDate.withDayOfMonth(1);
            case ALL -> null;
        };
        return new RankingPeriodWindow(from, rankingDate);
    }

    public boolean contains(LocalDate date) {
        return date != null && !date.isAfter(to) && (from == null || !date.isBefore(from));
    }
}
