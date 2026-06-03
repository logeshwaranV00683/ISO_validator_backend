package com.verinite.rules.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
    @Column(name = "rule_id")
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
    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    @Column(name = "pattern_regex", length = 500)
    private String patternRegex;

    // CRITICAL | WARNING | INFO
    @Column(name = "severity", nullable = false, length = 10)
    @Builder.Default
    private String severity = "CRITICAL";

    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 1;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_by", nullable = false)
    @Builder.Default
    private Long createdBy = 0L;

    @Column(name = "created_by_name", nullable = false, length = 100)
    @Builder.Default
    private String createdByName = "system";

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_by_name", length = 100)
    private String updatedByName;

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL,
            fetch = FetchType.EAGER, orphanRemoval = true)
    @Builder.Default
    private List<RuleAllowedValue> allowedValues = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (createdBy == null) createdBy = 0L;
        if (createdByName == null) createdByName = "system";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}