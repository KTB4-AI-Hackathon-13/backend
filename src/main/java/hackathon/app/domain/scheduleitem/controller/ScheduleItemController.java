package hackathon.app.domain.scheduleitem.controller;

import hackathon.app.domain.scheduleitem.dto.ScheduleItemCreateRequest;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemResponse;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemStatusResponse;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemStatusUpdateRequest;
import hackathon.app.domain.scheduleitem.dto.ScheduleItemUpdateRequest;
import hackathon.app.domain.scheduleitem.service.ScheduleItemService;
import hackathon.app.global.auth.LoginUser;
import hackathon.app.global.auth.LoginUserInfo;
import hackathon.app.global.common.ApiPaths;
import hackathon.app.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 6. 작업 API — /api/v1/schedules/{scheduleId}/items, /api/v1/schedule-items/{itemId} */
@RestController
@RequestMapping(ApiPaths.V1)
@RequiredArgsConstructor
public class ScheduleItemController {

    private final ScheduleItemService scheduleItemService;

    /** 작업 추가 → 201 */
    @PostMapping("/schedules/{scheduleId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleItemResponse> createItem(@LoginUser LoginUserInfo loginUser,
                                                        @PathVariable Long scheduleId,
                                                        @Valid @RequestBody ScheduleItemCreateRequest request) {
        return ApiResponse.of(scheduleItemService.createItem(loginUser.userId(), scheduleId, request));
    }

    /** 작업 수정 */
    @PatchMapping("/schedule-items/{itemId}")
    public ApiResponse<ScheduleItemResponse> updateItem(@LoginUser LoginUserInfo loginUser,
                                                        @PathVariable Long itemId,
                                                        @Valid @RequestBody ScheduleItemUpdateRequest request) {
        return ApiResponse.of(scheduleItemService.updateItem(loginUser.userId(), itemId, request));
    }

    /** 작업 상태 변경 (완료 시 퍼즐 조각 지급 결과 포함) */
    @PatchMapping("/schedule-items/{itemId}/status")
    public ApiResponse<ScheduleItemStatusResponse> changeStatus(@LoginUser LoginUserInfo loginUser,
                                                                @PathVariable Long itemId,
                                                                @Valid @RequestBody ScheduleItemStatusUpdateRequest request) {
        return ApiResponse.of(scheduleItemService.changeStatus(loginUser.userId(), itemId, request.status()));
    }

    /** 작업 삭제 (소프트 삭제) → 204 */
    @DeleteMapping("/schedule-items/{itemId}")
    public ResponseEntity<Void> deleteItem(@LoginUser LoginUserInfo loginUser, @PathVariable Long itemId) {
        scheduleItemService.deleteItem(loginUser.userId(), itemId);
        return ResponseEntity.noContent().build();
    }
}
