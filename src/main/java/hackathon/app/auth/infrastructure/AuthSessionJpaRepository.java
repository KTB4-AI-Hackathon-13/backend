package hackathon.app.auth.infrastructure;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import hackathon.app.auth.domain.AuthSession;
public interface AuthSessionJpaRepository extends JpaRepository<AuthSession, String> {
    List<AuthSession> findAllByUserId(Long userId);
}
