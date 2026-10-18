/**
 * 
 */
package com.skgrp.tempmail.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/**
 * This is called entity. A layer where the data is mapped to the database via variables basically.
 */
@Entity
@Table(name = "email_messages", indexes = {
	    @Index(name = "idx_recipient", columnList = "recipient")
	})
public class EmailMessage {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String recipient;

    private String subject;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, updatable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    public EmailMessage() {}

    public EmailMessage(String sender, String recipient, String subject, String body) {
        this.sender = sender;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.receivedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
}
