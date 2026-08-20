package hackathon.app.ai.plan.controller;

import hackathon.app.ai.plan.dto.GenerateTemplateRequest;
import hackathon.app.ai.plan.dto.GenerateScheduleRequest;
import hackathon.app.ai.plan.dto.ReviseScheduleRequest;
import hackathon.app.ai.plan.dto.TemplateResponse;
import hackathon.app.ai.plan.service.AiPlanService;
import hackathon.app.common.api.ApiResponse;
import hackathon.app.domain.schedule.dto.ScheduleDetailResponse;
import hackathon.app.global.auth.LoginUser;
import hackathon.app.global.auth.LoginUserInfo;
import hackathon.app.global.common.ApiPaths;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/schedules")
@RequiredArgsConstructor
public class AiPlanController {
    private final AiPlanService aiPlanService;

    /** 최초 목표 입력 → 외부 AI의 POST /templates 호출 → 질문 템플릿 반환 */
    @PostMapping("/templates")
    public ApiResponse<TemplateResponse> generateTemplate(@LoginUser LoginUserInfo loginUser,
                                                           @Valid @RequestBody GenerateTemplateRequest request) {
        return ApiResponse.of(aiPlanService.generateTemplate(loginUser.userId(), request));
    }

    @PostMapping("/ai-generations")
    public ApiResponse<ScheduleDetailResponse> generate(@LoginUser LoginUserInfo loginUser,
                                                        @Valid @RequestBody GenerateScheduleRequest request) {
        return ApiResponse.of(aiPlanService.generate(loginUser.userId(), request));
    }

    @PostMapping("/{scheduleId}/ai-revisions")
    public ApiResponse<ScheduleDetailResponse> revise(@LoginUser LoginUserInfo loginUser,
                                                      @PathVariable Long scheduleId,
                                                      @Valid @RequestBody ReviseScheduleRequest request) {
        return ApiResponse.of(aiPlanService.revise(loginUser.userId(), scheduleId, request));
    }
}
