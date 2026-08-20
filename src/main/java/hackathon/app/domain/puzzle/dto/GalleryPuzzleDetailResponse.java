package hackathon.app.domain.puzzle.dto;

public record GalleryPuzzleDetailResponse(
        PuzzleDetailResponse puzzle,
        Long authorUserId,
        String authorNickname,
        long likeCount,
        boolean likedByMe
) {}
