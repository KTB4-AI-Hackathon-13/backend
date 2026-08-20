package hackathon.app.preference.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import hackathon.app.preference.domain.UserPreference;

public interface JpaUserPreferenceRepository extends JpaRepository<UserPreference, Long> {}
