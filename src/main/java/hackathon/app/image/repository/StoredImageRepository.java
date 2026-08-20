package hackathon.app.image.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import hackathon.app.image.entity.StoredImage;

public interface StoredImageRepository extends JpaRepository<StoredImage, Long> {
    Optional<StoredImage> findByIdAndDeletedAtIsNull(Long id);

    @Query(value = """
            SELECT i.* FROM images i
            JOIN categories c ON c.id = i.category_id
            WHERE c.code = :categoryCode
              AND c.is_active = TRUE
              AND i.deleted_at IS NULL
            ORDER BY RAND()
            LIMIT 1
            """, nativeQuery = true)
    Optional<StoredImage> findRandomActiveByCategoryCode(@Param("categoryCode") String categoryCode);
}
