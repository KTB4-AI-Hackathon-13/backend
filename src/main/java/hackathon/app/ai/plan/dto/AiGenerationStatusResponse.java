package hackathon.app.ai.plan.dto;

import hackathon.app.ai.plan.entity.AiGenerationJob;
import hackathon.app.ai.plan.entity.AiGenerationStatus;
import hackathon.app.domain.scheduleitem.entity.ScheduleItem;
import hackathon.app.domain.scheduleitem.entity.ScheduleItemType;
import java.time.LocalDate;
import java.util.List;

public record AiGenerationStatusResponse(
        String generationId,
        AiGenerationStatus status,
        Long scheduleId,
        String failureReason,
        List<Item> items
) {
    public record Item(LocalDate scheduledDate, String title, Integer estimatedMinutes,
                       ScheduleItemType itemType, String description) {
        static Item from(ScheduleItem item) {
            return new Item(item.getScheduledDate(), item.getTitle(), item.getEstimatedMinutes(),
                    item.getItemType(), item.getDescription());
        }
    }

    public static AiGenerationStatusResponse of(AiGenerationJob job, List<ScheduleItem> items) {
        return new AiGenerationStatusResponse(job.getId(), job.getStatus(), job.getScheduleId(),
                job.getFailureReason(), items.stream().map(Item::from).toList());
    }
}
