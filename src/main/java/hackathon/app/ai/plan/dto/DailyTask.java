package hackathon.app.ai.plan.dto;

import java.time.LocalDate;

public record DailyTask(
        String id,
        LocalDate scheduled_date,
        String title,
        String description,
        Integer estimated_min
) {}
