package com.mb.ocrservice.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class AwsConfig {

    @Value("${aws.region:ap-south-1}")
    private String region;

    @Value("${aws.s3.endpoint:#{null}}")
    private String s3Endpoint;

    @Value("${aws.accessKey:#{null}}")
    private String accessKey;

    @Value("${aws.secretKey:#{null}}")
    private String secretKey;

    @Bean
    @Profile("local")
    public AmazonS3 amazonS3Local() {
        return AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration(
                                s3Endpoint != null ? s3Endpoint : "http://localhost:4566",
                                region
                        )
                )
                .withPathStyleAccessEnabled(true)
                .withCredentials(
                        new AWSStaticCredentialsProvider(
                                new BasicAWSCredentials(
                                        accessKey != null ? accessKey : "test",
                                        secretKey != null ? secretKey : "test"
                                )
                        )
                )
                .build();
    }

    @Bean
    @Profile("!local")
    public AmazonS3 amazonS3() {
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard()
                .withRegion(region);
        
        // If credentials are provided explicitly, use them
        if (accessKey != null && secretKey != null) {
            builder.withCredentials(
                    new AWSStaticCredentialsProvider(
                            new BasicAWSCredentials(accessKey, secretKey)
                    )
            );
        }
        // Otherwise, default credential provider chain will be used
        // (environment variables, Java system properties, credential profiles, or EC2/ECS instance profiles)
        
        return builder.build();
    }
}
