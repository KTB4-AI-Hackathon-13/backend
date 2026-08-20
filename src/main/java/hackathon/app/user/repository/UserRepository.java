package hackathon.app.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import hackathon.app.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    java.util.Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
