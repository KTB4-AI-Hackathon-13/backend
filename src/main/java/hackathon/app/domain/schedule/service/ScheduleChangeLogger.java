package hackathon.app.domain.schedule.service;

import hackathon.app.domain.schedule.entity.ChangeAction;
import hackathon.app.domain.schedule.entity.ChangeSource;
import hackathon.app.domain.schedule.entity.ScheduleChangeLog;
import hackathon.app.domain.schedule.repository.ScheduleChangeLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * schedule_change_logs 기록 담당.
 * before/after 는 임의 객체(Map, DTO 등)를 받아 JSON 문자열로 저장한다.
 */
@Component
@RequiredArgsConstructor
public class ScheduleChangeLogger {

    private final ScheduleChangeLogRepository changeLogRepository;
    private final ObjectMapper objectMapper;

    public ScheduleChangeLog log(Long scheduleId, Long scheduleItemId, Long actorUserId,
                                 ChangeAction action, ChangeSource source, int version,
                                 Object before, Object after, String reason) {
        ScheduleChangeLog log = ScheduleChangeLog.builder()
                .scheduleId(scheduleId)
                .scheduleItemId(scheduleItemId)
                .actorUserId(actorUserId)
                .action(action)
                .source(source)
                .version(version)
                .beforeData(toJson(before))
                .afterData(toJson(after))
                .reason(reason)
                .build();
        return changeLogRepository.save(log);
    }

    private String toJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }
}
