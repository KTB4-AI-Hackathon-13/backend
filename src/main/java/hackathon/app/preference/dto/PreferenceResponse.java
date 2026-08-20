package hackathon.app.preference.dto;

import hackathon.app.preference.entity.PuzzleVisibility;
import hackathon.app.preference.entity.UserPreference;

public record PreferenceResponse(int maxDailyTasks, boolean weekendScheduleEnabled,
        boolean aiRescheduleEnabled, boolean notificationEnabled,
        PuzzleVisibility defaultPuzzleVisibility, boolean rankingParticipationEnabled,
        boolean galleryNicknameVisible, boolean likeNotificationEnabled,
        boolean rankingChangeNotificationEnabled) {
    public static PreferenceResponse from(UserPreference preference) {
        return new PreferenceResponse(preference.getMaxDailyTasks(), preference.isWeekendScheduleEnabled(),
            preference.isAiRescheduleEnabled(), preference.isNotificationEnabled(),
            preference.getDefaultPuzzleVisibility(), preference.isRankingParticipationEnabled(),
            preference.isGalleryNicknameVisible(), preference.isLikeNotificationEnabled(),
            preference.isRankingChangeNotificationEnabled());
    }
}
