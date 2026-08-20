package hackathon.app.conversation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationApiIntegrationTests {
    private static final long USER_ID = 1L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ConversationRepository conversations;
    @Autowired ConversationMessageRepository messages;

    @BeforeEach
    void clean() {
        messages.deleteAll();
        conversations.deleteAll();
    }

    @Test
    void createsListsAndArchivesOwnedConversation() throws Exception {
        String id = createConversation("운동 계획");

        mvc.perform(authenticated(get("/api/v1/conversations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].conversationId").value(id))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));

        mvc.perform(authenticated(patch("/api/v1/conversations/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    void sendsDeterministicExchangeAndReturnsPlanReadiness() throws Exception {
        String id = createConversation(null);

        mvc.perform(authenticated(post("/api/v1/conversations/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"9월 30일까지 평일마다 운동할래\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userMessage.sequenceNo").value(1))
                .andExpect(jsonPath("$.data.assistantMessage.sequenceNo").value(2))
                .andExpect(jsonPath("$.data.assistantMessage.modelName").value("deterministic-mock-v1"))
                .andExpect(jsonPath("$.data.planReadiness.ready").value(true))
                .andExpect(jsonPath("$.data.planReadiness.missingFields").isEmpty());

        assertThat(messages.findMaxSequenceNo(id)).isEqualTo(2);
    }

    @Test
    void loadsOlderMessagesForInfiniteScrollUsingBeforeCursor() throws Exception {
        String id = createConversation(null);
        sendMessage(id, "첫 번째 메시지");
        sendMessage(id, "두 번째 메시지");
        sendMessage(id, "세 번째 메시지");

        mvc.perform(authenticated(get("/api/v1/conversations/{id}/messages", id).param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sequenceNo").value(5))
                .andExpect(jsonPath("$.data.items[1].sequenceNo").value(6))
                .andExpect(jsonPath("$.data.nextCursor").value(5))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        mvc.perform(authenticated(get("/api/v1/conversations/{id}/messages", id)
                        .param("size", "2").param("before", "5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sequenceNo").value(3))
                .andExpect(jsonPath("$.data.items[1].sequenceNo").value(4))
                .andExpect(jsonPath("$.data.nextCursor").value(3))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void revisionPreservesOriginalAndLinksReplacement() throws Exception {
        String id = createConversation(null);
        String response = mvc.perform(authenticated(post("/api/v1/conversations/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"운동하고 싶어\"}")))
                .andReturn().getResponse().getContentAsString();
        String originalId = objectMapper.readTree(response).at("/data/userMessage/id").asText();

        mvc.perform(authenticated(post("/api/v1/conversations/{id}/messages/{messageId}/revisions", id, originalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"9월 30일까지 주말마다 운동할래\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userMessage.replacesMessageId").value(originalId))
                .andExpect(jsonPath("$.data.userMessage.sequenceNo").value(3))
                .andExpect(jsonPath("$.data.assistantMessage.sequenceNo").value(4));

        assertThat(messages.findById(originalId)).isPresent();
        assertThat(messages.count()).isEqualTo(4);
    }

    @Test
    void validationAndNotFoundUseCommonErrorShape() throws Exception {
        mvc.perform(authenticated(post("/api/v1/conversations/unknown/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("content"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mvc.perform(authenticated(get("/api/v1/conversations/unknown/messages")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void archivedConversationRejectsNewMessages() throws Exception {
        String id = createConversation(null);
        mvc.perform(authenticated(patch("/api/v1/conversations/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ARCHIVED\"}")));

        mvc.perform(authenticated(post("/api/v1/conversations/{id}/messages", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"계속\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONVERSATION_ARCHIVED"));
    }

    private String createConversation(String title) throws Exception {
        String body = title == null ? "{}" : "{\"title\":\"" + title + "\"}";
        String response = mvc.perform(authenticated(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON).content(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.at("/data/conversationId").asText();
    }

    private void sendMessage(String conversationId, String content) throws Exception {
        mvc.perform(authenticated(post("/api/v1/conversations/{id}/messages", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}")))
                .andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", USER_ID);
    }
}
