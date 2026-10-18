package com.skgrp.tempmail.smtp;

import com.skgrp.tempmail.entity.EmailMessage;
import com.skgrp.tempmail.service.SseNotificationService;
import com.skgrp.tempmail.service.TempMailService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;
import org.subethamail.smtp.server.SMTPServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
public class SmtpServerService implements MessageHandlerFactory {

    private static final Logger log = LoggerFactory.getLogger(SmtpServerService.class);

    private final TempMailService tempMailService;
    private final SseNotificationService sseNotificationService;
    private final int smtpPort;
    private SMTPServer smtpServer;

    public SmtpServerService(
            TempMailService tempMailService,
            SseNotificationService sseNotificationService,
            @Value("${tempmail.smtp.port:2525}") int smtpPort) {
        this.tempMailService = tempMailService;
        this.sseNotificationService = sseNotificationService;
        this.smtpPort = smtpPort;
    }

    /**
     * Starts the embedded SMTP socket listener when the Spring context initializes.
     */
    @PostConstruct
    public void startServer() {
        this.smtpServer = SMTPServer.port(smtpPort)
                .messageHandlerFactory(this)
                .build();
        this.smtpServer.start();
        log.info(">>> Embedded SMTP Server listening on port {} <<<", smtpPort);
    }

    /**
     * Gracefully shuts down the socket when Spring Boot stops.
     */
    @PreDestroy
    public void stopServer() {
        if (smtpServer != null && smtpServer.isRunning()) {
            smtpServer.stop();
            log.info(">>> Embedded SMTP Server stopped <<<");
        }
    }

    /**
     * SubEthaSMTP calls this factory method for every new incoming TCP connection.
     */
    @Override
    public MessageHandler create(MessageContext ctx) {
        return new TempMailHandler();
    }

    /**
     * Inner handler that processes the state machine for an individual email session.
     */
    private class TempMailHandler implements MessageHandler {
        private String sender;
        private String recipient;

        @Override
        public void from(String from) throws RejectException {
            this.sender = from;
            log.info("SMTP: MAIL FROM received: {}", from);
        }

        @Override
        public void recipient(String recipient) throws RejectException {
            // Clean up surrounding brackets if present (e.g., "<user@temp.local>")
            String cleanRecipient = recipient.replace("<", "").replace(">", "").trim();
            this.recipient = cleanRecipient;
            log.info("SMTP: RCPT TO accepted: {}", cleanRecipient);
            // Notice: We do NOT throw RejectException here. That makes this a CATCH-ALL inbox!
        }

        @Override
        public String data(InputStream data) throws RejectException, TooMuchDataException, IOException {
            try {
                log.info("SMTP: Parsing incoming MIME stream for {}", recipient);

                MimeMessageParser.ParsedEmail parsed = MimeMessageParser.parse(data);

                String displayBody = !parsed.htmlBody().isBlank() 
                        ? parsed.htmlBody() 
                        : parsed.plainTextBody();

                // 1. Save to Database
                EmailMessage savedMessage = tempMailService.saveIncomingEmail(
                        this.sender != null ? this.sender : "unknown@sender",
                        this.recipient,
                        parsed.subject(),
                        displayBody
                );

                // 2. Real-time push to open browser listeners
                sseNotificationService.pushNewEmail(this.recipient, savedMessage);

                return "250 Ok: Message delivered";

            } catch (Exception e) {
                log.error("Failed to parse and save MIME message", e);
                throw new RejectException(451, "Error processing email content");
            }
        }

        @Override
        public void done() {
            log.info("SMTP: Session complete for recipient: {}", recipient);
        }
    }
}