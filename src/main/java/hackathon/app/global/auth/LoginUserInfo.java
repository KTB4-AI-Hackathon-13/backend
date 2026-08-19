package hackathon.app.global.auth;

/** 현재 로그인 사용자 정보. 필요 시 nickname, timezone 등을 확장한다. */
public record LoginUserInfo(Long userId) {
}
