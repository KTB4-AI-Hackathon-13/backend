package hackathon.app.domain.scheduleitem.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * user_preferences 테이블을 PK(user_id) 단건 조회로 읽는다. 엔티티를 두지 않아 2번 도메인 코드와 충돌하지 않는다.
 * 2번 담당자가 UserPreference 엔티티/서비스를 만들면 그걸 쓰는 구현으로 교체해도 된다.
 */
@Component
@RequiredArgsConstructor
public class JdbcDailyTaskLimitProvider implements DailyTaskLimitProvider {

    private final JdbcClient jdbcClient;

    @Override
    public int maxDailyTasks(Long userId) {
        return jdbcClient.sql("SELECT max_daily_tasks FROM user_preferences WHERE user_id = :userId")
                .param("userId", userId)
                .query(Integer.class)
                .optional()
                .orElse(DEFAULT_MAX_DAILY_TASKS);
    }
}
