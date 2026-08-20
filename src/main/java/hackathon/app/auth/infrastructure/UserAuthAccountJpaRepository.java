package hackathon.app.auth.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import hackathon.app.auth.domain.UserAuthAccount;
import hackathon.app.auth.domain.AuthProvider;
import java.util.Optional;
public interface UserAuthAccountJpaRepository extends JpaRepository<UserAuthAccount, Long> {
    Optional<UserAuthAccount> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
