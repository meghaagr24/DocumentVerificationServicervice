package com.mb.ocrservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for standardized error responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponseDto {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    private String errorCode;
    private String message;
    private String details;
    private String path;
    private Map<String, Object> additionalInfo;

    public static ErrorResponseDto of(String errorCode, String message, String details) {
        return ErrorResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .build();
    }

    public static ErrorResponseDto of(String errorCode, String message, String details, String path) {
        return ErrorResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .path(path)
                .build();
    }

    public static ErrorResponseDto of(String errorCode, String message, String details, String path, Map<String, Object> additionalInfo) {
        return ErrorResponseDto.builder()
                .timestamp(LocalDateTime.now())
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .path(path)
                .additionalInfo(additionalInfo)
                .build();
    }
} 