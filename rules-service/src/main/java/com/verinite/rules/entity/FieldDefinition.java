package com.verinite.rules.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "field_definitions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class FieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "definition_id")
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

    // numeric | alpha | alphanumeric | binary | special
    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    @Column(name = "max_length", nullable = false)
    private Integer maxLength;

    @Column(name = "is_llvar", nullable = false)
    @Builder.Default
    private Boolean isLlvar = false;

    @Column(name = "is_lllvar", nullable = false)
    @Builder.Default
    private Boolean isLllvar = false;

    @Column(name = "is_mandatory", nullable = false)
    @Builder.Default
    private Boolean isMandatory = false;

    @Column(name = "placeholder_value", length = 500)
    private String placeholderValue;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_builder_visible", nullable = false)
    @Builder.Default
    private Boolean isBuilderVisible = true;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (createdBy == null)     createdBy = 0L;
        if (createdByName == null) createdByName = "system";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}