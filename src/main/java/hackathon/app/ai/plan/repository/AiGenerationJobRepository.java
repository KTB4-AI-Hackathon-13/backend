package hackathon.app.ai.plan.repository;

import hackathon.app.ai.plan.entity.AiGenerationJob;
import hackathon.app.ai.plan.entity.AiGenerationStatus;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGenerationJobRepository extends JpaRepository<AiGenerationJob, String> {
    Optional<AiGenerationJob> findByIdAndUserId(String id, Long userId);
    boolean existsByUserIdAndConversationIdAndStatusIn(Long userId, String conversationId,
                                                       Collection<AiGenerationStatus> statuses);
}
