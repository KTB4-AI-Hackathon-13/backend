package hackathon.app.conversation;

import hackathon.app.common.api.ApiResponse;
import hackathon.app.ai.plan.service.ConversationPlanService;
import hackathon.app.conversation.dto.request.*;
import hackathon.app.conversation.dto.response.*;
import hackathon.app.global.auth.LoginUser;
import hackathon.app.global.auth.LoginUserInfo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    private final ConversationService service;
    private final ConversationPlanService planService;

    public ConversationController(ConversationService service, ConversationPlanService planService) {
        this.service = service;
        this.planService = planService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ConversationResponse> create(@LoginUser LoginUserInfo loginUser,
            @Valid @RequestBody CreateConversationRequest body) {
        return ApiResponse.of(service.create(loginUser.userId(), body.title()));
    }

    @GetMapping
    ApiResponse<CursorPageResponse<ConversationResponse>> list(@LoginUser LoginUserInfo loginUser,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) {
        int safeSize = Math.max(1, Math.min(size, 100));
        return ApiResponse.of(service.list(loginUser.userId(), safeSize, cursor));
    }

    @GetMapping("/{conversationId}")
    ApiResponse<ConversationResponse> find(@LoginUser LoginUserInfo loginUser,
            @PathVariable String conversationId) {
        return ApiResponse.of(service.find(loginUser.userId(), conversationId));
    }

    @GetMapping("/{conversationId}/messages")
    ApiResponse<MessageScrollResponse> messages(@LoginUser LoginUserInfo loginUser, @PathVariable String conversationId,
            @RequestParam(defaultValue = "50") int size, @RequestParam(required = false) Integer before) {
        int safeSize = Math.max(1, Math.min(size, 100));
        return ApiResponse.of(service.messages(loginUser.userId(), conversationId, safeSize, before));
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ConversationTurnResponse> send(@LoginUser LoginUserInfo loginUser, @PathVariable String conversationId,
            @Valid @RequestBody MessageRequest body) {
        return ApiResponse.of(planService.turn(loginUser.userId(), conversationId, body));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<MessageResponse> revise(@LoginUser LoginUserInfo loginUser,
            @PathVariable String conversationId, @PathVariable String messageId,
            @Valid @RequestBody MessageRequest body) {
        return ApiResponse.of(service.revise(loginUser.userId(), conversationId, messageId, body.message()));
    }

    @PatchMapping("/{conversationId}")
    ApiResponse<ConversationResponse> archive(@LoginUser LoginUserInfo loginUser,
            @PathVariable String conversationId,
            @Valid @RequestBody UpdateConversationRequest body) {
        return ApiResponse.of(service.archive(loginUser.userId(), conversationId, body.status()));
    }
}
