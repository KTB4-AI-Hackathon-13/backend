package hackathon.app.ai.plan.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiPlanClientConfig {
    @Bean("aiPlanRestClient")
    RestClient aiPlanRestClient(@Value("${app.ai.base-url:}") String baseUrl,
            @Value("${app.ai.connect-timeout-seconds:5}") long connectSeconds,
            @Value("${app.ai.read-timeout-seconds:60}") long readSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));
        RestClient.Builder configured = RestClient.builder().requestFactory(factory);
        if (!baseUrl.isBlank()) configured.baseUrl(baseUrl);
        return configured.build();
    }
}
