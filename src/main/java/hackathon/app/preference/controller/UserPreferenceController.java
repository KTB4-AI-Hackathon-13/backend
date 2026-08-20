package hackathon.app.preference.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import hackathon.app.common.api.ApiResponse;
import hackathon.app.preference.dto.PreferenceResponse;
import hackathon.app.preference.dto.UpdatePreferenceRequest;
import hackathon.app.preference.entity.UserPreference;
import hackathon.app.preference.service.UserPreferenceService;

@RestController
@RequestMapping("/api/v1/users/me/preferences")
public class UserPreferenceController {
    private final UserPreferenceService service;
    public UserPreferenceController(UserPreferenceService service) { this.service = service; }
    @GetMapping
    ApiResponse<PreferenceResponse> get(@CookieValue(name="SESSION", required=false) String sessionId) {
        return ApiResponse.of(PreferenceResponse.from(service.get(sessionId)));
    }
    @PatchMapping
    ApiResponse<PreferenceResponse> update(@CookieValue(name="SESSION", required=false) String sessionId,
        @Valid @RequestBody UpdatePreferenceRequest request) {
        UserPreference updated = service.update(sessionId, new UserPreferenceService.UpdateCommand(
            request.maxDailyTasks(), request.weekendScheduleEnabled(), request.aiRescheduleEnabled(),
            request.notificationEnabled(), request.defaultPuzzleVisibility(),
            request.rankingParticipationEnabled(), request.galleryNicknameVisible(),
            request.likeNotificationEnabled(), request.rankingChangeNotificationEnabled()));
        return ApiResponse.of(PreferenceResponse.from(updated));
    }
}
