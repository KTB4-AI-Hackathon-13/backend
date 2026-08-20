package hackathon.app.category.repository;

import hackathon.app.category.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();
    Optional<Category> findByNameAndActiveTrue(String name);
}
