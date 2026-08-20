package hackathon.app.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 255)
    private String email;
    @Column(name = "password_hash", length = 255)
    private String passwordHash;
    @Column(nullable = false, unique = true, length = 50)
    private String nickname;
    @Column(name = "profile_image_id")
    private Long profileImageId;
    @Column(nullable = false, length = 50)
    private String timezone;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private UserStatus status;
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    protected User() {}

    public static User create(String email, String passwordHash, String nickname) {
        User user = new User();
        user.email = email.toLowerCase();
        user.passwordHash = passwordHash;
        user.nickname = nickname;
        user.timezone = "Asia/Seoul";
        user.status = UserStatus.ACTIVE;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = user.createdAt;
        return user;
    }

    public void login() { this.lastLoginAt = LocalDateTime.now(); this.updatedAt = lastLoginAt; }
    public void updateProfile(String nickname, Long profileImageId, String timezone) {
        if (nickname != null) this.nickname = nickname;
        if (profileImageId != null) this.profileImageId = profileImageId;
        if (timezone != null) this.timezone = timezone;
        this.updatedAt = LocalDateTime.now();
    }
    public void changePassword(String passwordHash) { this.passwordHash = passwordHash; this.updatedAt = LocalDateTime.now(); }
    public void withdraw() { this.status = UserStatus.WITHDRAWN; this.withdrawnAt = LocalDateTime.now(); this.updatedAt = withdrawnAt; }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public Long getProfileImageId() { return profileImageId; }
    public String getTimezone() { return timezone; }
    public UserStatus getStatus() { return status; }
}
