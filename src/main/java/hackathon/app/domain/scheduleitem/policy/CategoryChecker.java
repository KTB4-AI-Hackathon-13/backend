package hackathon.app.domain.scheduleitem.policy;

/** categories 테이블에 사용 가능한(is_active) 카테고리가 있는지 확인한다. 카테고리 도메인 엔티티가 생기면 교체 가능. */
public interface CategoryChecker {

    boolean existsActive(Long categoryId);
}
