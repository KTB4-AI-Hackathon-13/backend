package hackathon.app.preference.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import hackathon.app.preference.entity.UserPreference;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    Optional<UserPreference> findByUserId(Long userId);
}
