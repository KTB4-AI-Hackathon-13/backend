package hackathon.app.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import hackathon.app.user.domain.User;

public interface JpaUserRepository extends JpaRepository<User, Long> {
    java.util.Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
