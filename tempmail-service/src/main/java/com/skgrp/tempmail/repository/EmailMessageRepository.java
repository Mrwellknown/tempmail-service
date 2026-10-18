package com.skgrp.tempmail.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.skgrp.tempmail.entity.EmailMessage;
@Repository
public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {
	List<EmailMessage> findByRecipientIgnoreCaseOrderByReceivedAtDesc(String recipient);
	
	@Modifying
    @Query("DELETE FROM EmailMessage e WHERE e.receivedAt < :cutoffTime")
    int deleteExpiredEmails(@Param("cutoffTime") LocalDateTime cutoffTime);
}