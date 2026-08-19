package hackathon.app.domain.schedule.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hackathon.app.domain.schedule.dto.ScheduleSummaryResponse;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import hackathon.app.domain.schedule.service.ScheduleService;
import hackathon.app.global.auth.HeaderLoginUserProvider;
import hackathon.app.global.auth.LoginUserArgumentResolver;
import hackathon.app.global.common.CursorPage;
import hackathon.app.global.common.RequestIdFilter;
import hackathon.app.global.config.WebConfig;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.common.error.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScheduleController.class)
@Import({WebConfig.class, LoginUserArgumentResolver.class, HeaderLoginUserProvider.class,
        GlobalExceptionHandler.class, RequestIdFilter.class})
class ScheduleControllerTest {

    private static final String BASE = "/api/v1/schedules";

    @Autowired MockMvc mockMvc;
    @MockitoBean ScheduleService scheduleService;

    private ScheduleSummaryResponse summary() {
        return new ScheduleSummaryResponse(101L, "8월 알고리즘 공부", ScheduleStatus.ACTIVE,
                LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 30), 1, 7, 2);
    }

    @Test
    @DisplayName("GET /schedules — 공통 응답 포맷 {data:{items,nextCursor,hasNext}, meta.requestId}")
    void getSchedules_returnsCursorPage() throws Exception {
        when(scheduleService.getSchedules(eq(1L), isNull(), isNull(), isNull()))
                .thenReturn(CursorPage.of(List.of(summary()), "MTAx", true));

        mockMvc.perform(get(BASE).header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.data.items[0].id").value(101))
                .andExpect(jsonPath("$.data.items[0].puzzleCount").value(7))
                .andExpect(jsonPath("$.data.items[0].completedPuzzleCount").value(2))
                .andExpect(jsonPath("$.data.items[0].startDate").value("2026-08-17"))
                .andExpect(jsonPath("$.data.items[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].source").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").value("MTAx"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /schedules — X-User-Id 없으면 401 AUTHENTICATION_REQUIRED")
    void getSchedules_withoutUser_returns401() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /schedules?size=0 — 400 INVALID_REQUEST + fieldErrors")
    void getSchedules_invalidSize_returns400() throws Exception {
        mockMvc.perform(get(BASE).header("X-User-Id", "1").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
    }

    @Test
    @DisplayName("GET /schedules/{id} — 없으면 404 SCHEDULE_NOT_FOUND")
    void getSchedule_notFound_returns404() throws Exception {
        when(scheduleService.getSchedule(1L, 999L)).thenThrow(new ApiException(ErrorCode.SCHEDULE_NOT_FOUND));

        mockMvc.perform(get(BASE + "/999").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("GET /schedules/{id} — 타인 소유면 403 FORBIDDEN")
    void getSchedule_forbidden_returns403() throws Exception {
        when(scheduleService.getSchedule(1L, 201L)).thenThrow(new ApiException(ErrorCode.FORBIDDEN));

        mockMvc.perform(get(BASE + "/201").header("X-User-Id", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH /schedules/{id} — 본문 검증 실패(title 201자)면 400 + fieldErrors")
    void updateSchedule_invalidBody_returns400() throws Exception {
        String body = "{\"title\":\"" + "a".repeat(201) + "\"}";

        mockMvc.perform(patch(BASE + "/101").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    @DisplayName("PATCH /schedules/{id} — 기간 밖 작업 존재 시 409 ITEMS_OUTSIDE_SCHEDULE_PERIOD")
    void updateSchedule_conflict_returns409() throws Exception {
        when(scheduleService.updateSchedule(eq(1L), eq(101L), any()))
                .thenThrow(new ApiException(ErrorCode.ITEMS_OUTSIDE_SCHEDULE_PERIOD));

        mockMvc.perform(patch(BASE + "/101").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endDate\":\"2026-08-20\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEMS_OUTSIDE_SCHEDULE_PERIOD"));
    }

    @Test
    @DisplayName("PATCH /schedules/{id} — 성공 시 200 + 수정된 스케줄")
    void updateSchedule_success_returns200() throws Exception {
        when(scheduleService.updateSchedule(eq(1L), eq(101L), any())).thenReturn(summary());

        mockMvc.perform(patch(BASE + "/101").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"새 제목\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(101));
    }

    @Test
    @DisplayName("DELETE /schedules/{id} — 204 No Content")
    void deleteSchedule_returns204() throws Exception {
        mockMvc.perform(delete(BASE + "/101").header("X-User-Id", "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /schedules/{id} — 없으면 404")
    void deleteSchedule_notFound_returns404() throws Exception {
        doThrow(new ApiException(ErrorCode.SCHEDULE_NOT_FOUND)).when(scheduleService).deleteSchedule(1L, 999L);

        mockMvc.perform(delete(BASE + "/999").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_FOUND"));
    }
}
