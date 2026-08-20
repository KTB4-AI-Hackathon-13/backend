package hackathon.app.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;

import hackathon.app.ranking.enums.PeriodType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankingPeriodWindowTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 20);

    @Test
    @DisplayName("기간별 시작일을 캘린더 기준으로 계산한다")
    void createsCalendarWindows() {
        assertThat(RankingPeriodWindow.of(PeriodType.DAILY, DATE).from()).isEqualTo(DATE);
        assertThat(RankingPeriodWindow.of(PeriodType.WEEKLY, DATE).from())
                .isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(RankingPeriodWindow.of(PeriodType.MONTHLY, DATE).from())
                .isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(RankingPeriodWindow.of(PeriodType.YEARLY, DATE).from())
                .isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(RankingPeriodWindow.of(PeriodType.ALL, DATE).from()).isNull();
    }

    @Test
    @DisplayName("기간 밖 활동은 포함하지 않는다")
    void containsOnlyWindowDates() {
        RankingPeriodWindow weekly = RankingPeriodWindow.of(PeriodType.WEEKLY, DATE);

        assertThat(weekly.contains(LocalDate.of(2026, 8, 16))).isFalse();
        assertThat(weekly.contains(LocalDate.of(2026, 8, 17))).isTrue();
        assertThat(weekly.contains(DATE)).isTrue();
        assertThat(weekly.contains(DATE.plusDays(1))).isFalse();
    }
}
