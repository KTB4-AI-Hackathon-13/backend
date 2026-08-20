package hackathon.app.ai.plan.client;

import hackathon.app.ai.plan.dto.*;
import hackathon.app.common.error.ApiException;
import hackathon.app.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RemoteAiPlanClient implements AiPlanClient {
    private static final Logger log = LoggerFactory.getLogger(RemoteAiPlanClient.class);
    private final RestClient client;
    private final boolean configured;
    public RemoteAiPlanClient(@Qualifier("aiPlanRestClient") RestClient client,
            @Value("${app.ai.base-url:}") String baseUrl) {
        this.client = client; this.configured = !baseUrl.isBlank();
    }
    public TemplateResponse generateTemplate(AiTemplatePayload request) {
        return post("/templates", request, TemplateResponse.class);
    }
    public PlanTurnResponse generate(AiGeneratePayload request) {
        return post("/plan/generate", request, PlanTurnResponse.class);
    }
    public PlanTurnResponse revise(AiRevisePayload request) {
        return post("/plan/revise", request, PlanTurnResponse.class);
    }
    private <T> T post(String path, Object body, Class<T> responseType) {
        if (!configured) throw new ApiException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        try {
            T result = client.post().uri(path).body(body).retrieve().body(responseType);
            if (result == null) throw new ApiException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
            return result;
        } catch (RestClientResponseException exception) {
            log.warn("AI 호출 실패 path={} status={} body={}", path,
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            if (exception.getStatusCode().value() == 429) throw new ApiException(ErrorCode.AI_RATE_LIMITED);
            throw new ApiException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("AI 호출 실패(응답 처리 오류) path={}", path, exception);
            throw new ApiException(ErrorCode.AI_PROVIDER_UNAVAILABLE);
        }
    }
}
