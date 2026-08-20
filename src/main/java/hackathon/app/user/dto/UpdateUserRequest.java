package hackathon.app.user.dto;

public record UpdateUserRequest(String nickname, Long profileImageId, String timezone) {}
