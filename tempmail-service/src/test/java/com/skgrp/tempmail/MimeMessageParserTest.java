package com.skgrp.tempmail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.skgrp.tempmail.smtp.MimeMessageParser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MimeMessageParser Unit Tests")
class MimeMessageParserTest {

    private InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Nested
    @DisplayName("Plain Text & Basic Header Tests")
    class PlainTextTests {

        @Test
        @DisplayName("[Positive] Standard plain text email with subject and body")
        void testParse_StandardPlainText() {
            String rawEmail = """
                    From: sender@example.com
                    To: recipient@temp.local
                    Subject: Hello World
                    Content-Type: text/plain; charset=utf-8

                    This is a plain text email body.
                    """;

            MimeMessageParser.ParsedEmail parsed = MimeMessageParser.parse(toStream(rawEmail));

            assertThat(parsed.subject()).isEqualTo("Hello World");
            assertThat(parsed.plainTextBody()).isEqualTo("This is a plain text email body.");
            assertThat(parsed.htmlBody()).isEmpty();
        }

        @Test
        @DisplayName("[Edge] Missing subject header defaults to '(No Subject)'")
        void testParse_MissingSubject() {
            String rawEmail = """
                    From: sender@example.com
                    To: recipient@temp.local
                    Content-Type: text/plain; charset=utf-8

                    Body without a subject header.
                    """;

            MimeMessageParser.ParsedEmail parsed = MimeMessageParser.parse(toStream(rawEmail));

            assertThat(parsed.subject()).isEqualTo("(No Subject)");
            assertThat(parsed.plainTextBody()).isEqualTo("Body without a subject header.");
        }

        @Test
        @DisplayName("[Edge] Blank or whitespace-only subject defaults to '(No Subject)'")
        void testParse_BlankSubject() {
            String rawEmail = """
                    From: sender@example.com
                    Subject:    \t  
                    Content-Type: text/plain

                    Testing blank subject line.
                    """;

            MimeMessageParser.ParsedEmail parsed = MimeMessageParser.parse(toStream(rawEmail));

            assertThat(parsed.subject()).isEqualTo("(No Subject)");
        }
    }

    @Nested
    @DisplayName("HTML & Multipart Tests")
    class MultipartTests {

        @Test
        @DisplayName("[Positive] Pure HTML email")
        void testParse_PureHtmlEmail() {
            String rawEmail = """
                    From: newsletter@domain.com
                    Subject: HTML Newsletter
                    Content-Type: text/html; charset=utf-8

                    <html><body><h1>Welcome!</h1><p>Click here</p></body></html>
                    """;

            MimeMessageParser.ParsedEmail parsed = MimeMessageParser.parse(toStream(rawEmail));

            assertThat(parsed.subject()).isEqualTo("HTML Newsletter");
            assertThat(parsed.htmlBody()).contains("<h1>Welcome!</h1>");
            assertThat(parsed.plainTextBody()).isEmpty();
        }

        @Test
        @DisplayName("[Positive] Multipart/alternative email with both plain text and HTML")
        void testParse_MultipartAlternative() {
            String rawEmail = """
                    From: service@alerts.com
                    Subject: Alert Notification
                    MIME-Version: 1.0
                    Content-Type: multipart/alternative; boundary="BOUNDARY_123"

                    --BOUNDARY_123
                    Content-Type: text/plain; charset=utf-8

                    Plain text fallback.
                    --BOUNDARY_123
                    Content-Type: text/html; charset=utf-8

                    <b>Rich HTML representation.</b>
                    --BOUNDARY_123--
                    """;

            MimeMessageParser.ParsedEmail parsed = MimeMessageParser.parse(toStream(rawEmail));

            assertThat(parsed.subject()).isEqualTo("Alert Notification");
            assertThat(parsed.plainTextBody()).isEqualTo("Plain text fallback.");
            assertThat(parsed.htmlBody()).isEqualTo("<b>Rich HTML representation.</b>");
        }

        @Test
        @DisplayName("[Edge] Multipart with attachments filters out attachment bodies from main text")
        void testParse_MultipartWithAttachment() {
            String rawEmail = """
                    From: client@work.com
                    Subject: Invoice
                    MIME-Version: 1.0
                    Content-Type: multipart/mixed; boundary="BOUNDARY_MIXED"

                    --BOUNDARY_MIXED
                    Content-Type: text/plain; charset=utf-8

                    Please find your attached invoice.
                    --BOUNDARY_MIXED
                    Content-Type: text/plain; name="invoice.txt"
                    Content-Disposition: attachment; filename="invoice.txt"

                    ATTACHMENT DATA - SHOULD NOT BE IN BODY
                    --BOUNDARY_MIXED--
                    """;

            MimeMessageParser.ParsedEmail parsed = MimeMessageParser.parse(toStream(rawEmail));

            assertThat(parsed.plainTextBody()).isEqualTo("Please find your attached invoice.");
            assertThat(parsed.plainTextBody()).doesNotContain("ATTACHMENT DATA");
        }
    }

    @Nested
    @DisplayName("Error & Boundary Tests")
    class ErrorAndBoundaryTests {

        @Test
        @DisplayName("[Edge] Empty input stream gracefully returns fallback object")
        void testParse_EmptyInputStream() {
            MimeMessageParser.ParsedEmail parsed = MimeMessageParser.parse(toStream(""));

            assertThat(parsed.subject()).isIn("(No Subject)", "(Parsing Failed)");
            assertThat(parsed.plainTextBody()).isEmpty();
            assertThat(parsed.htmlBody()).isEmpty();
        }

        @Test
        @DisplayName("[Negative] Corrupted or invalid MIME payload returns '(Parsing Failed)' without throwing uncaught exception")
        void testParse_MalformedContent() {
            byte[] garbageBytes = new byte[] { (byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x01 };
            InputStream corruptedStream = new ByteArrayInputStream(garbageBytes);

            MimeMessageParser.ParsedEmail parsed = MimeMessageParser.parse(corruptedStream);

            assertThat(parsed).isNotNull();
            assertThat(parsed.subject()).isIn("(No Subject)", "(Parsing Failed)");
        }
    }
}