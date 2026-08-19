package hackathon.app.domain.schedule.controller;

import hackathon.app.domain.schedule.dto.CalendarResponse;
import hackathon.app.domain.schedule.dto.TodayItemsResponse;
import hackathon.app.domain.schedule.service.ScheduleCalendarService;
import hackathon.app.global.auth.LoginUser;
import hackathon.app.global.auth.LoginUserInfo;
import hackathon.app.global.common.ApiPaths;
import hackathon.app.global.common.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 5. 스케줄 API — 날짜 기준 조회 (/calendar, /schedule-items/today) */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
@Validated
public class ScheduleCalendarController {

    private final ScheduleCalendarService calendarService;

    /** 월별 캘린더: year, month */
    @GetMapping("/calendar")
    public ApiResponse<CalendarResponse> getCalendar(@LoginUser LoginUserInfo loginUser,
                                                     @RequestParam @Min(2000) @Max(2100) int year,
                                                     @RequestParam @Min(1) @Max(12) int month) {
        return ApiResponse.of(calendarService.getCalendar(loginUser.userId(), year, month));
    }

    /** 오늘 할 일 */
    @GetMapping("/schedule-items/today")
    public ApiResponse<TodayItemsResponse> getToday(@LoginUser LoginUserInfo loginUser) {
        return ApiResponse.of(calendarService.getToday(loginUser.userId()));
    }
}
