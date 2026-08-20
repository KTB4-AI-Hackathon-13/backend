package hackathon.app.category.repository;

import hackathon.app.category.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();
}
