package hackathon.app.domain.schedule.controller;

import hackathon.app.domain.schedule.dto.ScheduleDetailResponse;
import hackathon.app.domain.schedule.dto.ScheduleSummaryResponse;
import hackathon.app.domain.schedule.dto.ScheduleUpdateRequest;
import hackathon.app.domain.schedule.entity.ScheduleStatus;
import hackathon.app.domain.schedule.service.ScheduleService;
import hackathon.app.global.auth.LoginUser;
import hackathon.app.global.auth.LoginUserInfo;
import hackathon.app.global.common.ApiPaths;
import hackathon.app.common.api.ApiResponse;
import hackathon.app.global.common.CursorPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 5. 스케줄 API — /api/v1/schedules */
@RestController
@RequestMapping(ApiPaths.V1 + "/schedules")
@RequiredArgsConstructor
@Validated
public class ScheduleController {

    private final ScheduleService scheduleService;

    /** 스케줄 목록: status?, size?, cursor? */
    @GetMapping
    public ApiResponse<CursorPage<ScheduleSummaryResponse>> getSchedules(
            @LoginUser LoginUserInfo loginUser,
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(required = false) @Min(1) @Max(ScheduleService.MAX_PAGE_SIZE) Integer size,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.of(scheduleService.getSchedules(loginUser.userId(), status, size, cursor));
    }

    /** 스케줄 상세 */
    @GetMapping("/{scheduleId}")
    public ApiResponse<ScheduleDetailResponse> getSchedule(@LoginUser LoginUserInfo loginUser,
                                                           @PathVariable Long scheduleId) {
        return ApiResponse.of(scheduleService.getSchedule(loginUser.userId(), scheduleId));
    }

    /** 스케줄 수정: title?, description?, startDate?, endDate? */
    @PatchMapping("/{scheduleId}")
    public ApiResponse<ScheduleSummaryResponse> updateSchedule(@LoginUser LoginUserInfo loginUser,
                                                               @PathVariable Long scheduleId,
                                                               @Valid @RequestBody ScheduleUpdateRequest request) {
        return ApiResponse.of(scheduleService.updateSchedule(loginUser.userId(), scheduleId, request));
    }

    /** 스케줄 삭제 (소프트 삭제) → 204 */
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(@LoginUser LoginUserInfo loginUser,
                                               @PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(loginUser.userId(), scheduleId);
        return ResponseEntity.noContent().build();
    }
}
