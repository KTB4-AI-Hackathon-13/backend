package hackathon.app.category;

import java.util.Arrays;
import java.util.Optional;

public enum CategoryType {
    EXERCISE("운동"), DIET("다이어트"), MUSIC("음악"), STUDY("공부"), LANGUAGE("어학"),
    CAREER("커리어"), HABIT("습관"), MINDSET("마인드셋"), RELATIONSHIP("인간관계"), HOBBY("취미");

    private final String displayName;

    CategoryType(String displayName) { this.displayName = displayName; }
    public String code() { return name(); }
    public String displayName() { return displayName; }

    public static Optional<CategoryType> fromDisplayName(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim();
        return Arrays.stream(values()).filter(type -> type.displayName.equals(normalized)).findFirst();
    }
}
