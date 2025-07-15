package com.mb.ocrservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "extracted_fields")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedField extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocr_result_id", nullable = false)
    private OcrResult ocrResult;

    @NotBlank
    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "field_value")
    private String fieldValue;

    @DecimalMin("0.00")
    @DecimalMax("1.00")
    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "is_valid")
    private Boolean isValid;

    @Column(name = "validation_message")
    private String validationMessage;
}