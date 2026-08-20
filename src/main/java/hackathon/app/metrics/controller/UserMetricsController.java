package hackathon.app.metrics.controller;

import hackathon.app.common.api.ApiResponse;
import hackathon.app.global.auth.LoginUser;
import hackathon.app.global.auth.LoginUserInfo;
import hackathon.app.metrics.dto.UserMetricsResponse;
import hackathon.app.metrics.service.UserDailyMetricService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/metrics")
@RequiredArgsConstructor
public class UserMetricsController {

    private final UserDailyMetricService service;

    @GetMapping
    ApiResponse<UserMetricsResponse> getMetrics(
            @LoginUser LoginUserInfo loginUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId) {
        return ApiResponse.of(service.getMetrics(loginUser.userId(), from, to, categoryId));
    }
}
