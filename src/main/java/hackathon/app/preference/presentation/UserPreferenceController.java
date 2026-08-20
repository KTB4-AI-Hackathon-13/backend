package hackathon.app.preference.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;
import hackathon.app.common.api.ApiResponse;
import hackathon.app.preference.application.UserPreferenceService;
import hackathon.app.preference.domain.PuzzleVisibility;
import hackathon.app.preference.domain.UserPreference;

@RestController
@RequestMapping("/api/v1/users/me/preferences")
public class UserPreferenceController {
    private final UserPreferenceService service;
    public UserPreferenceController(UserPreferenceService service) { this.service = service; }
    public record UpdateRequest(@Min(1) @Max(10) Integer maxDailyTasks,
        Boolean weekendScheduleEnabled, Boolean aiRescheduleEnabled,
        Boolean notificationEnabled, PuzzleVisibility defaultPuzzleVisibility,
        Boolean rankingParticipationEnabled, Boolean galleryNicknameVisible,
        Boolean likeNotificationEnabled, Boolean rankingChangeNotificationEnabled,
        @Min(1) @Max(1440) Integer dailyAvailableMinutes) {}
    public record PreferenceResponse(int maxDailyTasks, boolean weekendScheduleEnabled,
        boolean aiRescheduleEnabled, boolean notificationEnabled,
        PuzzleVisibility defaultPuzzleVisibility, boolean rankingParticipationEnabled,
        boolean galleryNicknameVisible, boolean likeNotificationEnabled,
        boolean rankingChangeNotificationEnabled, Integer dailyAvailableMinutes) {
        static PreferenceResponse from(UserPreference p) {
            return new PreferenceResponse(p.getMaxDailyTasks(), p.isWeekendScheduleEnabled(),
                p.isAiRescheduleEnabled(), p.isNotificationEnabled(), p.getDefaultPuzzleVisibility(),
                p.isRankingParticipationEnabled(), p.isGalleryNicknameVisible(),
                p.isLikeNotificationEnabled(), p.isRankingChangeNotificationEnabled(),
                p.getDailyAvailableMinutes());
        }
    }
    @GetMapping
    ApiResponse<PreferenceResponse> get(@CookieValue(name="SESSION", required=false) String sessionId) {
        return ApiResponse.of(PreferenceResponse.from(service.get(sessionId)));
    }
    @PatchMapping
    ApiResponse<PreferenceResponse> update(@CookieValue(name="SESSION", required=false) String sessionId,
        @Valid @RequestBody UpdateRequest request) {
        UserPreference updated = service.update(sessionId, new UserPreferenceService.UpdateCommand(
            request.maxDailyTasks(), request.weekendScheduleEnabled(), request.aiRescheduleEnabled(),
            request.notificationEnabled(), request.defaultPuzzleVisibility(),
            request.rankingParticipationEnabled(), request.galleryNicknameVisible(),
            request.likeNotificationEnabled(), request.rankingChangeNotificationEnabled(),
            request.dailyAvailableMinutes()));
        return ApiResponse.of(PreferenceResponse.from(updated));
    }
}
