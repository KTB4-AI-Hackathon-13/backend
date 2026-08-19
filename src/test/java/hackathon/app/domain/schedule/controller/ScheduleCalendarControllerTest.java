package hackathon.app.domain.schedule.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hackathon.app.domain.schedule.dto.CalendarResponse;
import hackathon.app.domain.schedule.dto.DailyItemResponse;
import hackathon.app.domain.schedule.dto.TodayItemsResponse;
import hackathon.app.domain.schedule.service.ScheduleCalendarService;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.global.auth.HeaderLoginUserProvider;
import hackathon.app.global.auth.LoginUserArgumentResolver;
import hackathon.app.global.common.RequestIdFilter;
import hackathon.app.global.config.WebConfig;
import hackathon.app.common.error.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScheduleCalendarController.class)
@Import({WebConfig.class, LoginUserArgumentResolver.class, HeaderLoginUserProvider.class,
        GlobalExceptionHandler.class, RequestIdFilter.class})
class ScheduleCalendarControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ScheduleCalendarService calendarService;

    private DailyItemResponse item() {
        return new DailyItemResponse(1004L, 101L, "8월 알고리즘 공부", null, "DP 문제 1개", 0, 3, 1,
                ScheduleItemStatus.TODO, null);
    }

    @Test
    @DisplayName("GET /calendar?year&month — 날짜별 작업 목록")
    void getCalendar_returnsDays() throws Exception {
        CalendarResponse.Day day = new CalendarResponse.Day(LocalDate.of(2026, 8, 19), 1, 0, List.of(item()));
        when(calendarService.getCalendar(1L, 2026, 8)).thenReturn(new CalendarResponse(2026, 8, 1, 0, List.of(day)));

        mockMvc.perform(get("/api/v1/calendar").header("X-User-Id", "1").param("year", "2026").param("month", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-08-19"))
                .andExpect(jsonPath("$.data.days[0].items[0].scheduleTitle").value("8월 알고리즘 공부"));
    }

    @Test
    @DisplayName("GET /calendar — month=13 이면 400")
    void getCalendar_invalidMonth_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/calendar").header("X-User-Id", "1").param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("GET /calendar — year 누락이면 400")
    void getCalendar_missingYear_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/calendar").header("X-User-Id", "1").param("month", "8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("GET /schedule-items/today — 오늘 작업, 완료 수, 전체 수")
    void getToday_returnsItems() throws Exception {
        when(calendarService.getToday(1L))
                .thenReturn(new TodayItemsResponse(LocalDate.of(2026, 8, 19), 1, 0, List.of(item())));

        mockMvc.perform(get("/api/v1/schedule-items/today").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-08-19"))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.completedCount").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(1004));
    }
}
