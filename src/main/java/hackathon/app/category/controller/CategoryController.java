package hackathon.app.category.controller;

import hackathon.app.category.entity.Category;
import hackathon.app.category.repository.CategoryRepository;
import hackathon.app.common.api.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 카테고리 랭킹·작업 폼이 공통으로 사용하는 읽기 전용 마스터 API. */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository repository;

    public record CategoryResponse(Long id, String name, String description, String iconUrl) {
        static CategoryResponse from(Category category) {
            return new CategoryResponse(
                    category.getId(), category.getName(), category.getDescription(), category.getIconUrl());
        }
    }

    @GetMapping
    ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.of(repository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(CategoryResponse::from)
                .toList());
    }
}
