package com.mb.ocrservice.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.testcontainers.containers.localstack.LocalStackContainer;

/**
 * AWS configuration for test environment.
 * This configuration uses TestContainers LocalStack for S3 testing.
 */
@Configuration
@Profile("test")
public class TestAwsConfig {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    /**
     * Creates an AmazonS3 client configured to use the LocalStack container.
     * The client is configured with the endpoint URL and region from the LocalStack container.
     * 
     * @param localStack The LocalStack container
     * @return An AmazonS3 client
     */
    @Bean
    @Primary
    public AmazonS3 amazonS3(LocalStackContainer localStack) {
        // Ensure the LocalStack container is running
        if (!localStack.isRunning()) {
            throw new IllegalStateException("LocalStack container is not running");
        }
        
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(
                                localStack.getEndpointOverride(LocalStackContainer.Service.S3).toString(),
                                localStack.getRegion()
                        )
                )
                .withCredentials(
                        new AWSStaticCredentialsProvider(
                                new BasicAWSCredentials(
                                        localStack.getAccessKey(),
                                        localStack.getSecretKey()
                                )
                        )
                )
                .withPathStyleAccessEnabled(true)
                .build();
        
        // Create the bucket if it doesn't exist
        if (!s3Client.doesBucketExistV2(bucketName)) {
            s3Client.createBucket(bucketName);
        }
        
        return s3Client;
    }
}
