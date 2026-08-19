package hackathon.app.preference.domain;

import java.util.Optional;

public interface UserPreferenceRepository {
    UserPreference save(UserPreference preference);
    Optional<UserPreference> findByUserId(Long userId);
}
