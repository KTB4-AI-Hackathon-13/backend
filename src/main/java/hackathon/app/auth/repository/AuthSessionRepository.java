package hackathon.app.auth.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import hackathon.app.auth.entity.AuthSession;
public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {
    List<AuthSession> findAllByUserId(Long userId);
}
