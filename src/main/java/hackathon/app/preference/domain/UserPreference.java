package hackathon.app.preference.domain;

import java.time.LocalDateTime;
import jakarta.persistence.*;

@Entity
@Table(name = "user_preferences")
public class UserPreference {
    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "max_daily_tasks", nullable = false)
    private int maxDailyTasks;
    @Column(name = "weekend_schedule_enabled", nullable = false)
    private boolean weekendScheduleEnabled;
    @Column(name = "ai_reschedule_enabled", nullable = false)
    private boolean aiRescheduleEnabled;
    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled;
    @Enumerated(EnumType.STRING)
    @Column(name = "default_puzzle_visibility", nullable = false)
    private PuzzleVisibility defaultPuzzleVisibility;
    @Column(name = "ranking_participation_enabled", nullable = false)
    private boolean rankingParticipationEnabled;
    @Column(name = "gallery_nickname_visible", nullable = false)
    private boolean galleryNicknameVisible;
    @Column(name = "like_notification_enabled", nullable = false)
    private boolean likeNotificationEnabled;
    @Column(name = "ranking_change_notification_enabled", nullable = false)
    private boolean rankingChangeNotificationEnabled;
    @Column(name = "daily_available_minutes")
    private Integer dailyAvailableMinutes;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserPreference() {}

    public static UserPreference createDefault(Long userId) {
        UserPreference preference = new UserPreference();
        preference.userId = userId;
        preference.maxDailyTasks = 5;
        preference.weekendScheduleEnabled = true;
        preference.aiRescheduleEnabled = true;
        preference.notificationEnabled = true;
        preference.defaultPuzzleVisibility = PuzzleVisibility.PUBLIC;
        preference.rankingParticipationEnabled = true;
        preference.galleryNicknameVisible = true;
        preference.likeNotificationEnabled = true;
        preference.rankingChangeNotificationEnabled = true;
        preference.createdAt = LocalDateTime.now();
        preference.updatedAt = preference.createdAt;
        return preference;
    }

    public void update(Integer maxDailyTasks, Boolean weekendScheduleEnabled,
                       Boolean aiRescheduleEnabled, Boolean notificationEnabled,
                       PuzzleVisibility defaultPuzzleVisibility,
                       Boolean rankingParticipationEnabled, Boolean galleryNicknameVisible,
                       Boolean likeNotificationEnabled, Boolean rankingChangeNotificationEnabled,
                       Integer dailyAvailableMinutes) {
        if (maxDailyTasks != null) this.maxDailyTasks = maxDailyTasks;
        if (weekendScheduleEnabled != null) this.weekendScheduleEnabled = weekendScheduleEnabled;
        if (aiRescheduleEnabled != null) this.aiRescheduleEnabled = aiRescheduleEnabled;
        if (notificationEnabled != null) this.notificationEnabled = notificationEnabled;
        if (defaultPuzzleVisibility != null) this.defaultPuzzleVisibility = defaultPuzzleVisibility;
        if (rankingParticipationEnabled != null) this.rankingParticipationEnabled = rankingParticipationEnabled;
        if (galleryNicknameVisible != null) this.galleryNicknameVisible = galleryNicknameVisible;
        if (likeNotificationEnabled != null) this.likeNotificationEnabled = likeNotificationEnabled;
        if (rankingChangeNotificationEnabled != null) this.rankingChangeNotificationEnabled = rankingChangeNotificationEnabled;
        if (dailyAvailableMinutes != null) this.dailyAvailableMinutes = dailyAvailableMinutes;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(Integer maxDailyTasks, Boolean weekendScheduleEnabled,
                       Boolean aiRescheduleEnabled, Boolean notificationEnabled,
                       PuzzleVisibility defaultPuzzleVisibility,
                       Boolean rankingParticipationEnabled, Boolean galleryNicknameVisible,
                       Boolean likeNotificationEnabled, Boolean rankingChangeNotificationEnabled) {
        update(maxDailyTasks, weekendScheduleEnabled, aiRescheduleEnabled, notificationEnabled,
                defaultPuzzleVisibility, rankingParticipationEnabled, galleryNicknameVisible,
                likeNotificationEnabled, rankingChangeNotificationEnabled, null);
    }

    public Long getUserId() { return userId; }
    public int getMaxDailyTasks() { return maxDailyTasks; }
    public boolean isWeekendScheduleEnabled() { return weekendScheduleEnabled; }
    public boolean isAiRescheduleEnabled() { return aiRescheduleEnabled; }
    public boolean isNotificationEnabled() { return notificationEnabled; }
    public PuzzleVisibility getDefaultPuzzleVisibility() { return defaultPuzzleVisibility; }
    public boolean isRankingParticipationEnabled() { return rankingParticipationEnabled; }
    public boolean isGalleryNicknameVisible() { return galleryNicknameVisible; }
    public boolean isLikeNotificationEnabled() { return likeNotificationEnabled; }
    public boolean isRankingChangeNotificationEnabled() { return rankingChangeNotificationEnabled; }
    public Integer getDailyAvailableMinutes() { return dailyAvailableMinutes; }
}
