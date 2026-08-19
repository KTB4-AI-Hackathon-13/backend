package hackathon.app.image.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import hackathon.app.image.entity.StoredImage;

public interface StoredImageRepository extends JpaRepository<StoredImage, Long> {
    Optional<StoredImage> findByIdAndDeletedAtIsNull(Long id);
}
