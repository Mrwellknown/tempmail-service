package com.skgrp.tempmail.smtp;

import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.BodyPart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

public class MimeMessageParser {
	private static final Logger log = LoggerFactory.getLogger(MimeMessageParser.class);

    public record ParsedEmail(
        String subject,
        String plainTextBody,
        String htmlBody
    ) {}

    private MimeMessageParser() {}

    /**
     * Parses a raw InputStream into a structured ParsedEmail object.
     */
    public static ParsedEmail parse(InputStream rawDataStream) {
        try {
            // Jakarta Mail requires a Session object to create a MimeMessage
            Session session = Session.getDefaultInstance(new Properties());
            MimeMessage message = new MimeMessage(session, rawDataStream);

            // 1. Extract Subject (MimeMessage automatically handles RFC-2047 decode)
            String subject = message.getSubject();
            if (subject == null || subject.isBlank()) {
                subject = "(No Subject)";
            }

            // 2. Extract Body (traverse multipart tree if necessary)
            StringBuilder plainText = new StringBuilder();
            StringBuilder htmlText = new StringBuilder();

            extractContent(message, plainText, htmlText);

            return new ParsedEmail(
                subject,
                plainText.toString().trim(),
                htmlText.toString().trim()
            );

        } catch (Exception e) {
            log.error("Failed to parse MIME message, falling back to empty fields", e);
            return new ParsedEmail("(Parsing Failed)", "", "");
        }
    }

    /**
     * Recursive extractor to drill down nested Multipart structures:
     * multipart/mixed -> multipart/alternative -> text/plain & text/html
     */
    private static void extractContent(Part part, StringBuilder plainText, StringBuilder htmlText) throws Exception {
        if (part.isMimeType("text/plain")) {
            // Ignore plain text if it is marked as an attachment
            if (part.getDisposition() == null || !part.getDisposition().equalsIgnoreCase(Part.ATTACHMENT)) {
                plainText.append((String) part.getContent()).append("\n");
            }
        } else if (part.isMimeType("text/html")) {
            if (part.getDisposition() == null || !part.getDisposition().equalsIgnoreCase(Part.ATTACHMENT)) {
                htmlText.append((String) part.getContent()).append("\n");
            }
        } else if (part.isMimeType("multipart/*")) {
            MimeMultipart multiPart = (MimeMultipart) part.getContent();
            int count = multiPart.getCount();
            for (int i = 0; i < count; i++) {
                BodyPart bodyPart = multiPart.getBodyPart(i);
                extractContent(bodyPart, plainText, htmlText);
            }
        }
    }
}
