package com.herafi.chat_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import com.herafi.chat_service.client.ApplicationClient;
import com.herafi.chat_service.entity.Conversation;
import com.herafi.chat_service.entity.Message;
import com.herafi.chat_service.exception.ChatException;
import com.herafi.chat_service.repository.ConversationRepository;
import com.herafi.chat_service.repository.MessageRepository;

class ConversationServiceTests {

    private ConversationRepository conversationRepository;
    private MessageRepository messageRepository;
    private ApplicationClient applicationClient;
    private ConversationService service;

    private UUID applicationId;
    private UUID jobId;
    private UUID client;
    private UUID worker;
    private UUID stranger;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        messageRepository = mock(MessageRepository.class);
        applicationClient = mock(ApplicationClient.class);
        service = new ConversationService(conversationRepository, messageRepository, applicationClient);

        applicationId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        client = UUID.randomUUID();
        worker = UUID.randomUUID();
        stranger = UUID.randomUUID();
    }

    @Test
    void clientOpensTheConversationForAnApplication() {
        givenChatContext();
        when(conversationRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());
        when(conversationRepository.saveAndFlush(any(Conversation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Conversation conversation = service.open(applicationId, client);

        assertEquals(applicationId, conversation.getApplicationId());
        assertEquals(jobId, conversation.getJobId());
        assertEquals(client, conversation.getClientId());
        assertEquals(worker, conversation.getWorkerId());
    }

    @Test
    void workerCannotStartTheConversation() {
        givenChatContext();
        when(conversationRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());

        ChatException exception = assertThrows(ChatException.class, () -> service.open(applicationId, worker));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("CONVERSATION_ACCESS_DENIED", exception.getCode());
        verify(conversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void strangerCannotStartTheConversation() {
        givenChatContext();
        when(conversationRepository.findByApplicationId(applicationId)).thenReturn(Optional.empty());

        assertThrows(ChatException.class, () -> service.open(applicationId, stranger));
        verify(conversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void openingTwiceReturnsTheSameConversationWithoutAskingAgain() {
        Conversation existing = conversation();
        when(conversationRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(existing));

        assertSame(existing, service.open(applicationId, client));
        verifyNoInteractions(applicationClient);
        verify(conversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void workerMayOpenAConversationThatAlreadyExists() {
        Conversation existing = conversation();
        when(conversationRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(existing));

        assertSame(existing, service.open(applicationId, worker));
    }

    @Test
    void strangerCannotOpenAnExistingConversation() {
        when(conversationRepository.findByApplicationId(applicationId)).thenReturn(Optional.of(conversation()));

        assertThrows(ChatException.class, () -> service.open(applicationId, stranger));
    }

    @Test
    void aLostRaceReadsTheConversationTheWinnerWrote() {
        Conversation winner = conversation();
        givenChatContext();
        when(conversationRepository.findByApplicationId(applicationId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(conversationRepository.saveAndFlush(any(Conversation.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate application_id"));

        assertSame(winner, service.open(applicationId, client));
    }

    @Test
    void sendingStoresTheMessageAndReportsTheOtherParticipant() {
        Conversation existing = conversation();
        when(conversationRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConversationService.Delivery delivery = service.send(existing.getId(), client, "hello");

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        assertEquals(client, saved.getValue().getSenderId());
        assertEquals("hello", saved.getValue().getBody());
        assertEquals(worker, delivery.recipientId());
    }

    @Test
    void theRecipientIsTheOtherSideWhicheverWayRound() {
        Conversation existing = conversation();
        when(conversationRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(client, service.send(existing.getId(), worker, "hello").recipientId());
    }

    @Test
    void strangersCannotSend() {
        Conversation existing = conversation();
        when(conversationRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        ChatException exception = assertThrows(ChatException.class,
                () -> service.send(existing.getId(), stranger, "hello"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void strangersCannotReadHistory() {
        Conversation existing = conversation();
        when(conversationRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThrows(ChatException.class, () -> service.messages(existing.getId(), stranger, 0, 20));
        verifyNoInteractions(messageRepository);
    }

    @Test
    void unknownConversationIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(conversationRepository.findById(unknown)).thenReturn(Optional.empty());

        ChatException exception = assertThrows(ChatException.class,
                () -> service.getForParticipant(unknown, client));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("CONVERSATION_NOT_FOUND", exception.getCode());
    }

    private void givenChatContext() {
        when(applicationClient.getChatContext(applicationId)).thenReturn(
                new ApplicationClient.ChatContext(applicationId, jobId, worker, client, "PENDING"));
    }

    private Conversation conversation() {
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setApplicationId(applicationId);
        conversation.setJobId(jobId);
        conversation.setClientId(client);
        conversation.setWorkerId(worker);
        return conversation;
    }
}
