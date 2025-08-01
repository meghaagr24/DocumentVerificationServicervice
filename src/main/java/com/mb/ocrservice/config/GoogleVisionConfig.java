package com.mb.ocrservice.config;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
@Slf4j
@Profile({"default", "local", "test"})  // Active in local and test environments only
public class GoogleVisionConfig {
    
    @Value("${google.vision.credentials-file-path}")
    private String credentialsFilePath;
    
    @Value("${spring.profiles.active:default}")
    private String activeProfile;
    
    @Bean
    public ImageAnnotatorClient imageAnnotatorClient() {
        // For development/testing, return a mock client if credentials file doesn't exist
        File credentialsFile = null;
        try {
            credentialsFile = ResourceUtils.getFile(credentialsFilePath);
        } catch (IOException e) {
            log.warn("Google Vision credentials file not found at: {}. Using mock client for development.", credentialsFilePath);
            return createMockClient();
        }
        
        // If credentials file exists, create a real client
        try {
            GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new FileInputStream(credentialsFile))
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
            
            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build();
            
            return ImageAnnotatorClient.create(settings);
        } catch (IOException e) {
            log.error("Failed to create Google Vision API client", e);
            // Fall back to mock client for development
            log.warn("Falling back to mock client for development");
            return createMockClient();
        }
    }
    
    private ImageAnnotatorClient createMockClient() {
        // Return null for now - this will be handled by the service layer
        return null;
    }
}
