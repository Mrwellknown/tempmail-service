package com.skgrp.tempmail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TempmailServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TempmailServiceApplication.class, args);
	}

}
