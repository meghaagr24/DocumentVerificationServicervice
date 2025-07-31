package com.mb.ocrservice.config;

import com.mb.ocrservice.dto.DocumentVerificationCompletedEvent;
import com.mb.ocrservice.dto.DocumentVerificationErrorEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for Kafka producers in the Document Verification Service.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Producer factory configuration for DocumentVerificationCompletedEvent.
     *
     * @return the producer factory
     */
    @Bean
    public ProducerFactory<String, DocumentVerificationCompletedEvent> completedEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Producer factory configuration for DocumentVerificationErrorEvent.
     *
     * @return the producer factory
     */
    @Bean
    public ProducerFactory<String, DocumentVerificationErrorEvent> errorEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka template for DocumentVerificationCompletedEvent.
     *
     * @return the Kafka template
     */
    @Bean
    public KafkaTemplate<String, DocumentVerificationCompletedEvent> completedEventKafkaTemplate() {
        return new KafkaTemplate<>(completedEventProducerFactory());
    }

    /**
     * Kafka template for DocumentVerificationErrorEvent.
     *
     * @return the Kafka template
     */
    @Bean
    public KafkaTemplate<String, DocumentVerificationErrorEvent> errorEventKafkaTemplate() {
        return new KafkaTemplate<>(errorEventProducerFactory());
    }
} 