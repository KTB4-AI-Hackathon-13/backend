package hackathon.app.ai.plan.dto;

import java.util.List;

/** AI가 생성하거나 수정하는 계획 본문. */
public record SchedulePlan(String summary, List<DailyTask> daily_tasks) {}
