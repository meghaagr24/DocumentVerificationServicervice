package com.mb.ocrservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class OcrServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OcrServiceApplication.class, args);
    }
}