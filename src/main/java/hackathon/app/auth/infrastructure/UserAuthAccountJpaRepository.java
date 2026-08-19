package hackathon.app.auth.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import hackathon.app.auth.domain.UserAuthAccount;
public interface UserAuthAccountJpaRepository extends JpaRepository<UserAuthAccount, Long> {}
