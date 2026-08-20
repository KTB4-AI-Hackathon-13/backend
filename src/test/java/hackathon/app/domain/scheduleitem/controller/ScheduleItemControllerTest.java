package hackathon.app.domain.scheduleitem.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hackathon.app.domain.scheduleitem.dto.ScheduleItemResponse;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemStatusResponse;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemStatus;
import hackathon.app.domain.scheduleitem.service.ScheduleItemService;
import hackathon.app.global.auth.HeaderLoginUserProvider;
import hackathon.app.global.auth.LoginUserArgumentResolver;
import hackathon.app.global.common.RequestIdFilter;
import hackathon.app.global.config.WebConfig;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.common.error.GlobalExceptionHandler;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScheduleItemController.class)
@Import({WebConfig.class, LoginUserArgumentResolver.class, HeaderLoginUserProvider.class,
        GlobalExceptionHandler.class, RequestIdFilter.class})
class ScheduleItemControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ScheduleItemService scheduleItemService;

    private ScheduleItemResponse item() {
        return new ScheduleItemResponse(2002L, 101L, null, null, "새 작업", null, LocalDate.of(2026, 8, 19),
                2, 1, 3, ScheduleItemStatus.TODO, null);
    }

    @Test
    @DisplayName("POST /schedule-items — 계획 없이 1일 스케줄과 작업 생성")
    void createStandaloneItem_returns201() throws Exception {
        ScheduleItemResponse standalone = new ScheduleItemResponse(2002L, null, null, null,
                "물 마시기", null, LocalDate.of(2026, 8, 19),
                0, 1, 3, ScheduleItemStatus.TODO, null);
        when(scheduleItemService.createStandaloneItem(eq(1L), any())).thenReturn(standalone);

        mockMvc.perform(post("/api/v1/schedule-items").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"물 마시기\",\"scheduledDate\":\"2026-08-19\",\"estimatedMinutes\":30}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(2002))
                .andExpect(jsonPath("$.data.scheduleId").isEmpty());
    }

    @Test
    @DisplayName("POST /schedules/{id}/items — 201 + 생성된 작업")
    void createItem_returns201() throws Exception {
        when(scheduleItemService.createItem(eq(1L), eq(101L), any())).thenReturn(item());

        mockMvc.perform(post("/api/v1/schedules/101/items").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"새 작업\",\"scheduledDate\":\"2026-08-19\",\"estimatedMinutes\":30}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(2002))
                .andExpect(jsonPath("$.data.position").value(2))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /schedules/{id}/items — title 누락이면 400 + fieldErrors")
    void createItem_missingTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/schedules/101/items").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"2026-08-19\",\"estimatedMinutes\":30}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }

    @Test
    @DisplayName("POST /schedules/{id}/items — 한도 초과면 422 MAX_DAILY_TASKS_EXCEEDED")
    void createItem_limit_returns422() throws Exception {
        when(scheduleItemService.createItem(eq(1L), eq(101L), any()))
                .thenThrow(new ApiException(ErrorCode.MAX_DAILY_TASKS_EXCEEDED));

        mockMvc.perform(post("/api/v1/schedules/101/items").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"scheduledDate\":\"2026-08-19\",\"estimatedMinutes\":30}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MAX_DAILY_TASKS_EXCEEDED"));
    }

    @Test
    @DisplayName("PATCH /schedule-items/{id} — 200 + 수정된 작업")
    void updateItem_returns200() throws Exception {
        when(scheduleItemService.updateItem(eq(1L), eq(2002L), any())).thenReturn(item());

        mockMvc.perform(patch("/api/v1/schedule-items/2002").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"새 작업\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2002));
    }

    @Test
    @DisplayName("PATCH /schedule-items/{id} — 기간 밖 날짜면 422 DATE_OUTSIDE_SCHEDULE_PERIOD")
    void updateItem_outsidePeriod_returns422() throws Exception {
        when(scheduleItemService.updateItem(eq(1L), eq(2002L), any()))
                .thenThrow(new ApiException(ErrorCode.DATE_OUTSIDE_SCHEDULE_PERIOD));

        mockMvc.perform(patch("/api/v1/schedule-items/2002").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scheduledDate\":\"2026-09-05\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DATE_OUTSIDE_SCHEDULE_PERIOD"));
    }

    @Test
    @DisplayName("PATCH /schedule-items/{id}/status — 설계서 '작업 완료 응답' 필드")
    void changeStatus_returnsCompletionFields() throws Exception {
        when(scheduleItemService.changeStatus(1L, 1004L, ScheduleItemStatus.COMPLETED))
                .thenReturn(new ScheduleItemStatusResponse(1004L, ScheduleItemStatus.COMPLETED,
                        OffsetDateTime.parse("2026-08-19T21:00:00+09:00"), false, null));

        mockMvc.perform(patch("/api/v1/schedule-items/1004/status").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemId").value(1004))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").value("2026-08-19T21:00:00+09:00"))
                .andExpect(jsonPath("$.data.puzzlePieceAwarded").value(false))
                .andExpect(jsonPath("$.data.puzzlePieceId").isEmpty());
    }

    @Test
    @DisplayName("PATCH /schedule-items/{id}/status — 잘못된 status 값이면 400")
    void changeStatus_invalidValue_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/schedule-items/1004/status").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("DELETE /schedule-items/{id} — 204")
    void deleteItem_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/schedule-items/2002").header("X-User-Id", "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /schedule-items/{id} — 타인 소유면 403")
    void deleteItem_forbidden_returns403() throws Exception {
        doThrow(new ApiException(ErrorCode.FORBIDDEN)).when(scheduleItemService).deleteItem(1L, 2001L);

        mockMvc.perform(delete("/api/v1/schedule-items/2001").header("X-User-Id", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("인증 헤더 없으면 401")
    void noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/schedule-items/2002"))
                .andExpect(status().isUnauthorized());
    }
}
