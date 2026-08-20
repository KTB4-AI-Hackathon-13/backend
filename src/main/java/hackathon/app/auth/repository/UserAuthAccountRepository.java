package hackathon.app.auth.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import hackathon.app.auth.entity.UserAuthAccount;
public interface UserAuthAccountRepository extends JpaRepository<UserAuthAccount, Long> {}
