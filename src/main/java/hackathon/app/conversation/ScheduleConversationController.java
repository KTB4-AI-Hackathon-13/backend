package hackathon.app.conversation;

import hackathon.app.common.api.ApiResponse;
import hackathon.app.conversation.dto.response.ConversationResponse;
import hackathon.app.global.auth.LoginUser;
import hackathon.app.global.auth.LoginUserInfo;
import hackathon.app.global.common.ApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/schedules")
@RequiredArgsConstructor
public class ScheduleConversationController {
    private final ConversationService conversationService;

    /** 이 스케줄을 생성한 AI 대화방 */
    @GetMapping("/{scheduleId}/conversation")
    public ApiResponse<ConversationResponse> getConversation(@LoginUser LoginUserInfo loginUser,
                                                              @PathVariable Long scheduleId) {
        return ApiResponse.of(conversationService.findBySchedule(loginUser.userId(), scheduleId));
    }
}
