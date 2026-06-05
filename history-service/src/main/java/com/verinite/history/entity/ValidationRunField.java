package com.verinite.history.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "validation_run_fields")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationRunField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @ToString.Exclude
    private ValidationRun run;

    @Column(name = "de_number", nullable = false, length = 10)
    private String deNumber;

    @Column(name = "field_name", length = 150)
    private String fieldName;

    @Column(name = "raw_value", columnDefinition = "LONGTEXT")
    private String rawValue;

    @Column(name = "display_value", length = 500)
    private String displayValue;

    @Column(name = "is_present", nullable = false)
    @Builder.Default
    private Boolean isPresent = false;

    @Column(name = "field_length")
    private Integer fieldLength;

    @Column(name = "de_position")
    private Integer dePosition;

    @Column(name = "encoding_type")
    @Enumerated(EnumType.STRING)
    private EncodingType encodingType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum EncodingType {
        FIXED, LLVAR, LLLVAR, MTI, BITMAP
    }
}