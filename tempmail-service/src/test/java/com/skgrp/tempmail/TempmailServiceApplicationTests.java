package com.skgrp.tempmail;

import com.skgrp.tempmail.entity.EmailMessage;
import com.skgrp.tempmail.repository.EmailMessageRepository;
import com.skgrp.tempmail.service.TempMailService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TempMailService Unit Tests")
class TempMailServiceTest {

    @Mock
    private EmailMessageRepository emailRepository;

    @InjectMocks
    private TempMailService tempMailService;

    private static final String DEFAULT_DOMAIN = "temp.local";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tempMailService, "domainName", DEFAULT_DOMAIN);
    }

    // =========================================================================
    // 1. generateDisposableAddress()
    // =========================================================================
    @Nested
    @DisplayName("generateDisposableAddress() Tests")
    class GenerateDisposableAddressTests {

        @Test
        @DisplayName("[Positive] Generates an 8-character hex token with domain")
        void testGenerateDisposableAddress_Positive() {
            String address = tempMailService.generateDisposableAddress();

            assertThat(address).isNotNull();
            assertThat(address).endsWith("@" + DEFAULT_DOMAIN);

            String prefix = address.split("@")[0];
            assertThat(prefix).hasSize(8);
            assertThat(prefix).matches("^[a-f0-9]{8}$");
        }

        @Test
        @DisplayName("[Positive] Successive calls produce unique addresses")
        void testGenerateDisposableAddress_Uniqueness() {
            String addr1 = tempMailService.generateDisposableAddress();
            String addr2 = tempMailService.generateDisposableAddress();

            assertThat(addr1).isNotEqualTo(addr2);
        }

        @Test
        @DisplayName("[Edge] Supports customized subdomain format")
        void testGenerateDisposableAddress_CustomSubdomain() {
            ReflectionTestUtils.setField(tempMailService, "domainName", "inbox.sub.domain.org");
            String address = tempMailService.generateDisposableAddress();

            assertThat(address).endsWith("@inbox.sub.domain.org");
            String prefix = address.split("@")[0];
            assertThat(prefix).hasSize(8);
        }
    }

    // =========================================================================
    // 2. saveIncomingEmail()
    // =========================================================================
    @Nested
    @DisplayName("saveIncomingEmail() Tests")
    class SaveIncomingEmailTests {

        @Test
        @DisplayName("[Positive] Standard email with all fields correctly persisted")
        void testSaveIncomingEmail_PositiveStandard() {
            String sender = "alice@sender.org";
            String recipient = "BOB@TEMP.LOCAL";
            String subject = "Meeting Notes";
            String body = "Here is the agenda.";

            EmailMessage savedEntity = new EmailMessage(sender, recipient.toLowerCase().trim(), subject, body);
            when(emailRepository.save(any(EmailMessage.class))).thenReturn(savedEntity);

            EmailMessage result = tempMailService.saveIncomingEmail(sender, recipient, subject, body);

            assertThat(result).isNotNull();
            assertThat(result.getRecipient()).isEqualTo("bob@temp.local");
            assertThat(result.getSender()).isEqualTo(sender);
            assertThat(result.getSubject()).isEqualTo(subject);
            assertThat(result.getBody()).isEqualTo(body);

            ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
            verify(emailRepository, times(1)).save(captor.capture());

            EmailMessage captured = captor.getValue();
            assertThat(captured.getRecipient()).isEqualTo("bob@temp.local");
            assertThat(captured.getReceivedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        @DisplayName("[Edge] Recipient normalization handles surrounding tabs and mixed case")
        void testSaveIncomingEmail_RecipientNormalization() {
            String rawRecipient = "   FooBar_123@TEMP.LOCAL   \t";
            when(emailRepository.save(any(EmailMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            EmailMessage result = tempMailService.saveIncomingEmail("sender@test.com", rawRecipient, "Subject", "Body");

            assertThat(result.getRecipient()).isEqualTo("foobar_123@temp.local");
        }

        @Test
        @DisplayName("[Edge] Handles null subject and blank body without breaking")
        void testSaveIncomingEmail_NullOrEmptyBodyAndSubject() {
            when(emailRepository.save(any(EmailMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            EmailMessage result = tempMailService.saveIncomingEmail("sender@test.com", "user@temp.local", null, "");

            assertThat(result.getSubject()).isNull();
            assertThat(result.getBody()).isEmpty();
        }

        @Test
        @DisplayName("[Edge] Supports large body payloads without clipping")
        void testSaveIncomingEmail_LargePayload() {
            String largeBody = "X".repeat(250_000); // 250 KB content
            when(emailRepository.save(any(EmailMessage.class))).thenAnswer(inv -> inv.getArgument(0));

            EmailMessage result = tempMailService.saveIncomingEmail("sender@test.com", "user@temp.local", "Large Payload", largeBody);

            assertThat(result.getBody()).hasSize(250_000);
        }

        @Test
        @DisplayName("[Negative] Repository throws exception on storage failure")
        void testSaveIncomingEmail_RepositoryFailure() {
            when(emailRepository.save(any(EmailMessage.class)))
                    .thenThrow(new RuntimeException("Database error: Out of memory"));

            assertThatThrownBy(() -> tempMailService.saveIncomingEmail("sender@test.com", "user@temp.local", "Subject", "Body"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Database error: Out of memory");

            verify(emailRepository, times(1)).save(any(EmailMessage.class));
        }

        @Test
        @DisplayName("[Negative] Null recipient causes NullPointerException upon trimming")
        void testSaveIncomingEmail_NullRecipient() {
            assertThatThrownBy(() -> tempMailService.saveIncomingEmail("sender@test.com", null, "Subject", "Body"))
                    .isInstanceOf(NullPointerException.class);

            verify(emailRepository, never()).save(any(EmailMessage.class));
        }
    }

    // =========================================================================
    // 3. getMessagesForRecipient()
    // =========================================================================
    @Nested
    @DisplayName("getMessagesForRecipient() Tests")
    class GetMessagesForRecipientTests {

        @Test
        @DisplayName("[Positive] Fetches message list for normalized recipient")
        void testGetMessagesForRecipient_Positive() {
            String recipient = "alice@temp.local";
            EmailMessage msg1 = new EmailMessage("sender1@test.com", recipient, "Msg 1", "Body 1");
            EmailMessage msg2 = new EmailMessage("sender2@test.com", recipient, "Msg 2", "Body 2");

            when(emailRepository.findByRecipientIgnoreCaseOrderByReceivedAtDesc("alice@temp.local"))
                    .thenReturn(List.of(msg2, msg1));

            List<EmailMessage> result = tempMailService.getMessagesForRecipient("  ALICE@TEMP.LOCAL  ");

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getSubject()).isEqualTo("Msg 2");
            assertThat(result.get(1).getSubject()).isEqualTo("Msg 1");

            verify(emailRepository, times(1)).findByRecipientIgnoreCaseOrderByReceivedAtDesc("alice@temp.local");
        }

        @Test
        @DisplayName("[Positive] Returns empty list when mailbox has no messages")
        void testGetMessagesForRecipient_EmptyMailbox() {
            when(emailRepository.findByRecipientIgnoreCaseOrderByReceivedAtDesc("empty@temp.local"))
                    .thenReturn(Collections.emptyList());

            List<EmailMessage> result = tempMailService.getMessagesForRecipient("empty@temp.local");

            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("[Negative] Null recipient throws NullPointerException")
        void testGetMessagesForRecipient_NullRecipient() {
            assertThatThrownBy(() -> tempMailService.getMessagesForRecipient(null))
                    .isInstanceOf(NullPointerException.class);

            verify(emailRepository, never()).findByRecipientIgnoreCaseOrderByReceivedAtDesc(any());
        }
    }
}