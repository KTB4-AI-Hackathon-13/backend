package hackathon.app.preference.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import hackathon.app.auth.application.AuthService;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import hackathon.app.preference.domain.PuzzleVisibility;
import hackathon.app.preference.domain.UserPreference;
import hackathon.app.preference.domain.UserPreferenceRepository;
import hackathon.app.user.domain.User;

@Service
@Transactional
public class UserPreferenceService {
    public record UpdateCommand(Integer maxDailyTasks, Boolean weekendScheduleEnabled,
        Boolean aiRescheduleEnabled, Boolean notificationEnabled,
        PuzzleVisibility defaultPuzzleVisibility, Boolean rankingParticipationEnabled,
        Boolean galleryNicknameVisible, Boolean likeNotificationEnabled,
        Boolean rankingChangeNotificationEnabled, Integer dailyAvailableMinutes) {
        public UpdateCommand(Integer maxDailyTasks, Boolean weekendScheduleEnabled,
                Boolean aiRescheduleEnabled, Boolean notificationEnabled,
                PuzzleVisibility defaultPuzzleVisibility, Boolean rankingParticipationEnabled,
                Boolean galleryNicknameVisible, Boolean likeNotificationEnabled,
                Boolean rankingChangeNotificationEnabled) {
            this(maxDailyTasks, weekendScheduleEnabled, aiRescheduleEnabled, notificationEnabled,
                    defaultPuzzleVisibility, rankingParticipationEnabled, galleryNicknameVisible,
                    likeNotificationEnabled, rankingChangeNotificationEnabled, null);
        }
    }

    private final UserPreferenceRepository preferences;
    private final AuthService auth;
    public UserPreferenceService(UserPreferenceRepository preferences, AuthService auth) {
        this.preferences = preferences; this.auth = auth;
    }
    public UserPreference get(String sessionId) {
        User user = auth.requireUser(sessionId);
        UserPreference preference = preferences.findByUserId(user.getId())
            .orElseGet(() -> preferences.save(UserPreference.createDefault(user.getId())));
        preference.enforceMvpPuzzleVisibility();
        return preference;
    }
    public UserPreference update(String sessionId, UpdateCommand command) {
        User user = auth.requireUser(sessionId);
        if (command.defaultPuzzleVisibility() == PuzzleVisibility.PRIVATE) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "MVP에서는 완성 퍼즐 공개 범위를 PUBLIC으로만 사용할 수 있습니다.");
        }
        UserPreference preference = preferences.findByUserId(user.getId())
            .orElseGet(() -> preferences.save(UserPreference.createDefault(user.getId())));
        preference.enforceMvpPuzzleVisibility();
        preference.update(command.maxDailyTasks(), command.weekendScheduleEnabled(),
            command.aiRescheduleEnabled(), command.notificationEnabled(),
            command.defaultPuzzleVisibility(), command.rankingParticipationEnabled(),
            command.galleryNicknameVisible(), command.likeNotificationEnabled(),
            command.rankingChangeNotificationEnabled(), command.dailyAvailableMinutes());
        return preference;
    }
}
