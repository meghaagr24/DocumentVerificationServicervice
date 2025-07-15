package com.mb.ocrservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DocumentVerificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentVerificationServiceApplication.class, args);
    }
}