package com.verinite.rules.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.verinite.common.enums.DataType;
import com.verinite.common.enums.Severity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "validation_rules")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@ToString(exclude = "allowedValues")
@EqualsAndHashCode(exclude = "allowedValues")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ValidationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")                        // schema PK is "id", not "rule_id"
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "profile_name", nullable = false, length = 100)
    private String profileName;

    @Column(name = "mti", nullable = false, length = 4)
    private String mti;

    @Column(name = "de_number", nullable = false, length = 10)
    private String deNumber;

    @Column(name = "field_name", nullable = false, length = 150)
    private String fieldName;

    @Column(name = "is_mandatory", nullable = false)
    @Builder.Default
    private Boolean isMandatory = false;

    @Column(name = "min_length")
    private Integer minLength;

    @Column(name = "max_length")
    private Integer maxLength;

    @Column(name = "exact_length")
    private Integer exactLength;

    // numeric | alpha | alphanumeric | binary | special
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private DataType dataType;

    @Column(name = "pattern_regex", length = 500) // EXISTS in schema, used for Java regex validation
    private String patternRegex;

    // CRITICAL | WARNING | INFO
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    @Builder.Default
    private Severity severity = Severity.CRITICAL;

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 1;

    @Column(name = "active", nullable = false)    // schema column is "active", not "is_active"
    @Builder.Default
    private Boolean active = true;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Soft-delete = deletedAt IS NULL means alive. Schema has deleted_at, NOT is_deleted.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)     // schema VARCHAR(50), not BIGINT
    private String createdBy;

    @Column(name = "updated_by", length = 50)     // schema VARCHAR(50), not BIGINT
    private String updatedBy;

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL,
            fetch = FetchType.EAGER, orphanRemoval = true)
    @Builder.Default
    private List<RuleAllowedValue> allowedValues = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (createdBy == null) createdBy = "system";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}