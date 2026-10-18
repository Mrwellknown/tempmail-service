package com.skgrp.tempmail.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skgrp.tempmail.entity.EmailMessage;
import com.skgrp.tempmail.repository.EmailMessageRepository;

@Service
public class TempMailService {
	private final EmailMessageRepository repository;

    @Value("${tempmail.domain:temp.local}")
    private String domainName;

    @Value("${tempmail.ttl.minutes:10}")
    private int ttlMinutes;

    public TempMailService(EmailMessageRepository repository) {
        this.repository = repository;
    }

    /**
     * Generates a random disposable email address.
     */
    public String generateDisposableAddress() {
        String randomHandle = UUID.randomUUID().toString().substring(0, 8);
        return randomHandle + "@" + domainName;
    }

    /**
     * Retrieves all received emails for a target mailbox.
     */
    @Transactional(readOnly = true)
    public List<EmailMessage> getMessagesForRecipient(String recipient) {
        return repository.findByRecipientIgnoreCaseOrderByReceivedAtDesc(recipient.trim().toLowerCase());
    }

    /**
     * Saves an incoming email message (called by the SMTP listener later in Step 3/4).
     */
    @Transactional
    public EmailMessage saveIncomingEmail(String sender, String recipient, String subject, String body) {
        // 1. Throws NullPointerException if recipient is null (Fixes Error #2)
        Objects.requireNonNull(recipient, "Recipient email cannot be null");

        // 2. Trims whitespace and normalizes to lowercase (Fixes Errors #1 and #3)
        String normalizedRecipient = recipient.trim().toLowerCase();

        EmailMessage message = new EmailMessage(sender, normalizedRecipient, subject, body);
        return repository.save(message);
    }

    /**
     * Scheduled cleanup job: purges messages older than the configured TTL (e.g., 10 minutes).
     * Runs every 60 seconds automatically.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupExpiredEmails() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ttlMinutes);
        int deletedCount = repository.deleteExpiredEmails(cutoff);
        if (deletedCount > 0) {
            System.out.println("Cleaned up " + deletedCount + " expired temp email(s).");
        }
    }
}
