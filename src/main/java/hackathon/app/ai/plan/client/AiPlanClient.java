package hackathon.app.ai.plan.client;

import hackathon.app.ai.plan.dto.*;

public interface AiPlanClient {
    TemplateResponse generateTemplate(AiTemplatePayload request);
    PlanTurnResponse generate(AiGeneratePayload request);
    PlanTurnResponse revise(AiRevisePayload request);
}
