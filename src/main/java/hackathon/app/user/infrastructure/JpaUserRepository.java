package hackathon.app.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import hackathon.app.user.domain.User;
import hackathon.app.user.domain.UserStatus;

import java.util.List;

public interface JpaUserRepository extends JpaRepository<User, Long> {
    java.util.Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndIdNot(String nickname, Long id);
    List<User> findAllByStatus(UserStatus status);
}
