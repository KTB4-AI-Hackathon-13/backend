package hackathon.app.global.config;

import hackathon.app.global.common.TimeUtils;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** "오늘" 계산 등에 사용하는 Clock. 테스트에서 고정 Clock 으로 교체 가능. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(TimeUtils.DEFAULT_ZONE);
    }
}
