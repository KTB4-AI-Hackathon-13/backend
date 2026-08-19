package hackathon.app.domain.scheduleitem.policy;

/**
 * 사용자의 "하루 최대 작업 수" 를 알려준다.
 * 출처: user_preferences.max_daily_tasks (2번 사용자 설정 도메인). 행이 없으면 기본값 5.
 */
public interface DailyTaskLimitProvider {

    int DEFAULT_MAX_DAILY_TASKS = 5;

    int maxDailyTasks(Long userId);
}
