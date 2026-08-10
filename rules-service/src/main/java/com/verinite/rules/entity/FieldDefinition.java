package com.verinite.rules.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.verinite.common.enums.DataType;
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
    @Column(name = "id")                          // schema PK is "id", not "definition_id"
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
    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private DataType dataType;

    @Column(name = "max_length")
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

    @Column(name = "active", nullable = false)    // schema column is "active", not "is_active"
    @Builder.Default
    private Boolean active = true;

    // Soft-delete = deletedAt IS NULL means alive. No is_deleted in schema.
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

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "source_max_length")
    private Integer sourceMaxLength;

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