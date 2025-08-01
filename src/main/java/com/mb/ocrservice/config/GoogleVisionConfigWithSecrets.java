package com.mb.ocrservice.config;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Configuration for Google Cloud Vision API with credentials from AWS Secrets Manager.
 * This configuration is used in production environments where the credentials are stored in AWS Secrets Manager.
 */
@Configuration
@Profile({"prod", "dev"})
public class GoogleVisionConfigWithSecrets {

    private static final Logger logger = LoggerFactory.getLogger(GoogleVisionConfigWithSecrets.class);

    @Value("${GOOGLE_CREDENTIALS_SECRET_ARN}")
    private String googleCredentialsSecretArn;

    @Value("${AWS_REGION:ap-south-1}")
    private String awsRegion;

    /**
     * Creates an ImageAnnotatorClient bean configured with Google Cloud Vision API credentials
     * fetched from AWS Secrets Manager.
     *
     * @return ImageAnnotatorClient instance
     * @throws IOException if there's an error reading the credentials
     */
    @Bean
    public ImageAnnotatorClient imageAnnotatorClient() {
        try {
            // Get the Google Cloud Vision API credentials from AWS Secrets Manager
            String credentialsJson = getGoogleCredentialsFromSecretsManager();
            
            // Create Google credentials from the JSON
            InputStream credentialsStream = new ByteArrayInputStream(credentialsJson.getBytes());
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream);
            
            // Configure the ImageAnnotatorSettings with the credentials
            ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                    .setCredentialsProvider(() -> credentials)
                    .build();
            
            // Create and return the ImageAnnotatorClient
            return ImageAnnotatorClient.create(settings);
        } catch (Exception e) {
            logger.error("Failed to create Google Vision API client from AWS Secrets Manager", e);
            logger.warn("Falling back to mock client for development");
            // Return null for now - this will be handled by the service layer
            return null;
        }
    }

    /**
     * Fetches the Google Cloud Vision API credentials from AWS Secrets Manager.
     *
     * @return The credentials JSON as a string
     */
    private String getGoogleCredentialsFromSecretsManager() {
        try {
            // Create an AWS Secrets Manager client
            AWSSecretsManager secretsManager = AWSSecretsManagerClientBuilder.standard()
                    .withRegion(awsRegion)
                    .build();
            
            // Create a request to get the secret value
            GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
                    .withSecretId(googleCredentialsSecretArn);
            
            // Get the secret value
            GetSecretValueResult getSecretValueResult = secretsManager.getSecretValue(getSecretValueRequest);
            
            // Return the secret string
            return getSecretValueResult.getSecretString();
        } catch (Exception e) {
            logger.error("Error fetching Google credentials from AWS Secrets Manager", e);
            throw new RuntimeException("Failed to fetch Google credentials from AWS Secrets Manager", e);
        }
    }
}
