package hackathon.app.ai.plan.client;

import hackathon.app.ai.plan.dto.*;

public interface AiPlanClient {
    TemplateResponse generateTemplate(AiTemplatePayload request);
    AiPlanResult generate(AiGeneratePayload request);
    AiPlanResult revise(AiRevisePayload request);
}
