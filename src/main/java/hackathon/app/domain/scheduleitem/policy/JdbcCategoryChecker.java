package hackathon.app.domain.scheduleitem.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcCategoryChecker implements CategoryChecker {

    private final JdbcClient jdbcClient;

    @Override
    public boolean existsActive(Long categoryId) {
        Integer count = jdbcClient
                .sql("SELECT COUNT(*) FROM categories WHERE id = :id AND is_active = TRUE")
                .param("id", categoryId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }
}
