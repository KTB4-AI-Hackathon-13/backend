package hackathon.app.preference.dto;

import hackathon.app.preference.entity.PuzzleVisibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdatePreferenceRequest(@Min(1) @Max(10) Integer maxDailyTasks,
        Boolean weekendScheduleEnabled, Boolean aiRescheduleEnabled,
        Boolean notificationEnabled, PuzzleVisibility defaultPuzzleVisibility,
        Boolean rankingParticipationEnabled, Boolean galleryNicknameVisible,
        Boolean likeNotificationEnabled, Boolean rankingChangeNotificationEnabled) {}
