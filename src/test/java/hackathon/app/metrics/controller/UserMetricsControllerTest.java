package hackathon.app.metrics.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hackathon.app.common.error.GlobalExceptionHandler;
import hackathon.app.global.auth.HeaderLoginUserProvider;
import hackathon.app.global.auth.LoginUserArgumentResolver;
import hackathon.app.global.common.RequestIdFilter;
import hackathon.app.global.config.WebConfig;
import hackathon.app.metrics.dto.UserMetricsResponse;
import hackathon.app.metrics.service.UserDailyMetricService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserMetricsController.class)
@Import({WebConfig.class, LoginUserArgumentResolver.class, HeaderLoginUserProvider.class,
        GlobalExceptionHandler.class, RequestIdFilter.class})
class UserMetricsControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserDailyMetricService service;

    @Test
    @DisplayName("GET /users/me/metrics — 기간·카테고리 달성 통계")
    void getMetrics_returnsSummaryAndDailyRows() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 19);
        LocalDate to = LocalDate.of(2026, 8, 20);
        UserMetricsResponse.DailyMetric daily = new UserMetricsResponse.DailyMetric(
                to, 2, 1, 1, new BigDecimal("50.00"), 2);
        when(service.getMetrics(1L, from, to, 10L)).thenReturn(new UserMetricsResponse(
                3, 2, 2, new BigDecimal("66.67"), 2, List.of(daily)));

        mockMvc.perform(get("/api/v1/users/me/metrics")
                        .header("X-User-Id", "1")
                        .param("from", "2026-08-19")
                        .param("to", "2026-08-20")
                        .param("categoryId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plannedItemCount").value(3))
                .andExpect(jsonPath("$.data.completedItemCount").value(2))
                .andExpect(jsonPath("$.data.puzzlePieceCount").value(2))
                .andExpect(jsonPath("$.data.achievementRate").value(66.67))
                .andExpect(jsonPath("$.data.consecutiveDays").value(2))
                .andExpect(jsonPath("$.data.daily[0].date").value("2026-08-20"));
    }

    @Test
    @DisplayName("GET /users/me/metrics — 인증이 없으면 401")
    void getMetrics_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/metrics")
                        .param("from", "2026-08-19")
                        .param("to", "2026-08-20"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("GET /users/me/metrics — 날짜 형식이 틀리면 400")
    void getMetrics_invalidDate_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/metrics")
                        .header("X-User-Id", "1")
                        .param("from", "2026-08-19")
                        .param("to", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
