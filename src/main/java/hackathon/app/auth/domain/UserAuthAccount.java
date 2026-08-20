package hackathon.app.auth.domain;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "user_auth_accounts")
public class UserAuthAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AuthProvider provider;
    @Column(name = "provider_user_id", nullable = false, length = 255) private String providerUserId;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    protected UserAuthAccount() {}
    public static UserAuthAccount local(Long userId, String email) {
        UserAuthAccount account = new UserAuthAccount();
        account.userId = userId; account.provider = AuthProvider.LOCAL;
        account.providerUserId = email.toLowerCase();
        account.createdAt = LocalDateTime.now(); account.updatedAt = account.createdAt;
        return account;
    }
    public static UserAuthAccount oauth(Long userId, AuthProvider provider, String providerUserId) {
        UserAuthAccount account = new UserAuthAccount();
        account.userId = userId; account.provider = provider;
        account.providerUserId = providerUserId;
        account.createdAt = LocalDateTime.now(); account.updatedAt = account.createdAt;
        return account;
    }
    public Long getUserId() { return userId; }
}
