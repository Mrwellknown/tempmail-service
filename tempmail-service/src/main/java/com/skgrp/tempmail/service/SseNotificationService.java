package com.skgrp.tempmail.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.skgrp.tempmail.entity.EmailMessage;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
@Service
public class SseNotificationService {
	
    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);

    // Timeout set to 30 minutes (matches or exceeds typical temp mailbox TTL)
    private static final Long EMITTER_TIMEOUT = 30 * 60 * 1000L;

    // Key: email address -> Value: List of active emitters (supports multiple browser tabs)
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Subscribes a client browser session to receive notifications for a specific mailbox.
     */
    public SseEmitter subscribe(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);

        emitters.computeIfAbsent(normalizedEmail, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Lifecycle cleanup handlers
        emitter.onCompletion(() -> removeEmitter(normalizedEmail, emitter));
        emitter.onTimeout(() -> removeEmitter(normalizedEmail, emitter));
        emitter.onError(e -> removeEmitter(normalizedEmail, emitter));

        try {
            // Send initial ping event to confirm the stream is alive
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Connected to live email stream for: " + normalizedEmail));
        } catch (IOException e) {
            removeEmitter(normalizedEmail, emitter);
        }

        return emitter;
    }

    /**
     * Pushes a newly received and parsed EmailMessage directly to listening clients.
     */
    public void pushNewEmail(String recipient, EmailMessage message) {
        String normalizedEmail = recipient.trim().toLowerCase();
        List<SseEmitter> clientEmitters = emitters.get(normalizedEmail);

        if (clientEmitters == null || clientEmitters.isEmpty()) {
            log.info("No active browser listeners for {}", normalizedEmail);
            return;
        }

        for (SseEmitter emitter : clientEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("NEW_EMAIL")
                        .data(message));
                log.info("Pushed real-time email event to client for {}", normalizedEmail);
            } catch (IOException e) {
                removeEmitter(normalizedEmail, emitter);
            }
        }
    }

    private void removeEmitter(String email, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(email);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(email);
            }
        }
    }
}
