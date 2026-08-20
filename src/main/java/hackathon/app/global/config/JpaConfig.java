package hackathon.app.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** created_at / updated_at 자동 채움 (BaseTimeEntity). */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
