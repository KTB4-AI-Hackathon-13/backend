package hackathon.app.domain.scheduleitem.policy;

import hackathon.app.preference.entity.UserPreference;
import hackathon.app.preference.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 사용자 설정 도메인의 UserPreferenceRepository 로 max_daily_tasks 를 읽는다. 설정 행이 없으면 기본값 5. */
@Component
@RequiredArgsConstructor
public class PreferenceDailyTaskLimitProvider implements DailyTaskLimitProvider {

    private final UserPreferenceRepository userPreferenceRepository;

    @Override
    public int maxDailyTasks(Long userId) {
        return userPreferenceRepository.findByUserId(userId)
                .map(UserPreference::getMaxDailyTasks)
                .orElse(DEFAULT_MAX_DAILY_TASKS);
    }
}
