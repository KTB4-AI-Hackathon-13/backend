package hackathon.app.user.infrastructure;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import hackathon.app.user.domain.User;
import hackathon.app.user.domain.UserRepository;

@Repository
public class UserRepositoryAdapter implements UserRepository {
    private final JpaUserRepository repository;
    public UserRepositoryAdapter(JpaUserRepository repository) { this.repository = repository; }
    public User save(User user) { return repository.save(user); }
    public Optional<User> findById(Long id) { return repository.findById(id); }
    public Optional<User> findByEmail(String email) { return repository.findByEmail(email.toLowerCase()); }
    public boolean existsByEmail(String email) { return repository.existsByEmail(email.toLowerCase()); }
    public boolean existsByNickname(String nickname) { return repository.existsByNickname(nickname); }
    public boolean existsByNicknameAndIdNot(String nickname, Long id) { return repository.existsByNicknameAndIdNot(nickname, id); }
}
