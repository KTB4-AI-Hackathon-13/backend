package hackathon.app.domain.puzzle.dto;

import hackathon.app.domain.puzzle.entity.Puzzle;
import hackathon.app.global.common.TimeUtils;
import java.time.OffsetDateTime;

/** 공개 갤러리 작품·작성자·완성일·좋아요 정보. */
public record GalleryPuzzleResponse(
        Long id,
        Long scheduleId,
        String title,
        Long imageId,
        Long authorUserId,
        String authorNickname,
        OffsetDateTime completedAt,
        long likeCount,
        boolean likedByMe
) {
    public static GalleryPuzzleResponse of(Puzzle puzzle, String nickname, long likeCount, boolean likedByMe) {
        return new GalleryPuzzleResponse(puzzle.getId(), puzzle.getScheduleId(), puzzle.getTitle(),
                puzzle.getImageId(), puzzle.getUserId(), nickname, TimeUtils.toOffset(puzzle.getCompletedAt()),
                likeCount, likedByMe);
    }
}
