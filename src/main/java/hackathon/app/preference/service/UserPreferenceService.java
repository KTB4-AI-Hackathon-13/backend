package hackathon.app.preference.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import hackathon.app.auth.service.AuthService;
import hackathon.app.preference.entity.PuzzleVisibility;
import hackathon.app.preference.entity.UserPreference;
import hackathon.app.preference.repository.UserPreferenceRepository;
import hackathon.app.user.entity.User;

@Service
@Transactional
public class UserPreferenceService {
    public record UpdateCommand(Integer maxDailyTasks, Boolean weekendScheduleEnabled,
        Boolean aiRescheduleEnabled, Boolean notificationEnabled,
        PuzzleVisibility defaultPuzzleVisibility, Boolean rankingParticipationEnabled,
        Boolean galleryNicknameVisible, Boolean likeNotificationEnabled,
        Boolean rankingChangeNotificationEnabled) {}

    private final UserPreferenceRepository preferences;
    private final AuthService auth;
    public UserPreferenceService(UserPreferenceRepository preferences, AuthService auth) {
        this.preferences = preferences; this.auth = auth;
    }
    public UserPreference get(String sessionId) {
        User user = auth.requireUser(sessionId);
        return preferences.findByUserId(user.getId())
            .orElseGet(() -> preferences.save(UserPreference.createDefault(user.getId())));
    }
    public UserPreference update(String sessionId, UpdateCommand command) {
        User user = auth.requireUser(sessionId);
        UserPreference preference = preferences.findByUserId(user.getId())
            .orElseGet(() -> preferences.save(UserPreference.createDefault(user.getId())));
        preference.update(command.maxDailyTasks(), command.weekendScheduleEnabled(),
            command.aiRescheduleEnabled(), command.notificationEnabled(),
            command.defaultPuzzleVisibility(), command.rankingParticipationEnabled(),
            command.galleryNicknameVisible(), command.likeNotificationEnabled(),
            command.rankingChangeNotificationEnabled());
        return preference;
    }
}
