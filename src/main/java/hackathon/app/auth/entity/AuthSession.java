package hackathon.app.auth.entity;

import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {
    @Id @Column(length = 36) private String id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "refresh_token_hash") private String refreshTokenHash;
    @Column(name = "user_agent", length = 500) private String userAgent;
    @Column(name = "ip_address", length = 45) private String ipAddress;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "last_used_at") private LocalDateTime lastUsedAt;
    protected AuthSession() {}
    public static AuthSession create(Long userId, String userAgent, String ipAddress, long days) {
        AuthSession session = new AuthSession();
        session.id = UUID.randomUUID().toString(); session.userId = userId;
        session.userAgent = userAgent; session.ipAddress = ipAddress;
        session.createdAt = LocalDateTime.now(); session.lastUsedAt = session.createdAt;
        session.expiresAt = session.createdAt.plusDays(days); return session;
    }
    public boolean isUsable() { return revokedAt == null && expiresAt.isAfter(LocalDateTime.now()); }
    public void revoke() { if (revokedAt == null) revokedAt = LocalDateTime.now(); }
    public String getId() { return id; }
    public Long getUserId() { return userId; }
}
