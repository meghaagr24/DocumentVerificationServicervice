package com.mb.ocrservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;

@Entity
@Table(name = "validation_results")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "is_authentic")
    private Boolean isAuthentic;

    @Column(name = "is_complete")
    private Boolean isComplete;

    @DecimalMin("0.00")
    @DecimalMax("1.00")
    @Column(name = "overall_confidence_score", precision = 5, scale = 2)
    private BigDecimal overallConfidenceScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_details", columnDefinition = "jsonb")
    private Map<String, Object> validationDetails;
}