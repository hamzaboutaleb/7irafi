package com.herafi.chat_service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.herafi.chat_service.entity.Conversation;
import com.herafi.chat_service.entity.Message;
import com.herafi.chat_service.exception.ChatException;
import com.herafi.chat_service.service.ConversationService;

@ActiveProfiles("test")
@WebMvcTest(ConversationController.class)
class ConversationControllerTests {

    /** Matches application-test.properties: the filter in front of /conversations. */
    private static final String TEST_GATEWAY_KEY = "test-gateway-key";
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID WORKER = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    void openingUsesTheHeaderIdentityNotTheBody() throws Exception {
        UUID applicationId = UUID.randomUUID();
        when(conversationService.open(eq(applicationId), any())).thenReturn(conversation());

        mockMvc.perform(post("/conversations")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", CLIENT.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"applicationId":"%s","clientId":"%s"}
                        """.formatted(applicationId, WORKER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(CLIENT.toString()));

        ArgumentCaptor<UUID> caller = ArgumentCaptor.forClass(UUID.class);
        verify(conversationService).open(eq(applicationId), caller.capture());
        assertEquals(CLIENT, caller.getValue());
    }

    @Test
    void openingWithoutAnIdentityHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(post("/conversations")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationId\":\"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void openingWithoutAnApplicationIdIsRejected() throws Exception {
        mockMvc.perform(post("/conversations")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", CLIENT.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void refusedAccessKeepsTheServiceErrorCode() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(conversationService.getForParticipant(eq(conversationId), any()))
                .thenThrow(new ChatException(HttpStatus.FORBIDDEN, "CONVERSATION_ACCESS_DENIED",
                        "You are not part of this conversation"));

        mockMvc.perform(get("/conversations/" + conversationId)
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", WORKER.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONVERSATION_ACCESS_DENIED"));
    }

    @Test
    void historyIsReturnedAsAPage() throws Exception {
        UUID conversationId = UUID.randomUUID();
        Message message = new Message();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversationId);
        message.setSenderId(CLIENT);
        message.setBody("hello");
        message.setCreatedAt(Instant.parse("2026-09-21T10:15:30Z"));
        Page<Message> page = new PageImpl<>(List.of(message), PageRequest.of(0, 20), 1);
        when(conversationService.messages(eq(conversationId), any(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/conversations/" + conversationId + "/messages")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", CLIENT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].text").value("hello"))
                .andExpect(jsonPath("$.content[0].senderId").value(CLIENT.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void historyRejectsAnOversizedPage() throws Exception {
        mockMvc.perform(get("/conversations/" + UUID.randomUUID() + "/messages")
                .header("X-Gateway-Key", TEST_GATEWAY_KEY)
                .header("X-User-Id", CLIENT.toString())
                .param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRequestThatDidNotComeFromTheGatewayIsRejected() throws Exception {
        mockMvc.perform(get("/conversations").header("X-User-Id", CLIENT.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNTRUSTED_GATEWAY"));
    }

    private Conversation conversation() {
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setApplicationId(UUID.randomUUID());
        conversation.setJobId(UUID.randomUUID());
        conversation.setClientId(CLIENT);
        conversation.setWorkerId(WORKER);
        conversation.setCreatedAt(Instant.now());
        return conversation;
    }
}
