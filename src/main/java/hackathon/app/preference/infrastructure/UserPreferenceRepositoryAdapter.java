package hackathon.app.preference.infrastructure;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import hackathon.app.preference.domain.UserPreference;
import hackathon.app.preference.domain.UserPreferenceRepository;

@Repository
public class UserPreferenceRepositoryAdapter implements UserPreferenceRepository {
    private final JpaUserPreferenceRepository repository;
    public UserPreferenceRepositoryAdapter(JpaUserPreferenceRepository repository) { this.repository = repository; }
    public UserPreference save(UserPreference preference) { return repository.save(preference); }
    public Optional<UserPreference> findByUserId(Long userId) { return repository.findById(userId); }
}
