package com.skgrp.tempmail.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.skgrp.tempmail.entity.EmailMessage;
import com.skgrp.tempmail.service.SseNotificationService;
import com.skgrp.tempmail.service.TempMailService;

@RestController
@RequestMapping("/api/v1/mailboxes")
@CrossOrigin(origins = "*")
public class MailboxController {
	private final TempMailService tempMailService;
	private final SseNotificationService notificationService;

	public MailboxController(TempMailService tempMailService, SseNotificationService notificationService) {
	    this.tempMailService = tempMailService;
	    this.notificationService = notificationService;
	}

    /**
     * POST /api/v1/mailboxes/generate
     * Returns a new temporary email address.
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generateMailbox() {
        String email = tempMailService.generateDisposableAddress();
        return ResponseEntity.ok(Map.of(
            "email", email,
            "status", "active",
            "message", "Mailbox ready to receive messages"
        ));
    }

    /**
     * GET /api/v1/mailboxes/{email}/messages
     * Fetches all emails received for the specified temporary mailbox.
     */
    @GetMapping("/{email}/messages")
    public ResponseEntity<List<EmailMessage>> getInbox(@PathVariable String email) {
        List<EmailMessage> messages = tempMailService.getMessagesForRecipient(email);
        return ResponseEntity.ok(messages);
    }
    /**
     * GET /api/v1/mailboxes/{email}/stream
     * Browser connects here via EventSource to receive instant updates.
     */
    @GetMapping(value = "/{email}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMailboxEvents(@PathVariable String email) {
        return notificationService.subscribe(email);
    }
}
