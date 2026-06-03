package com.verinite.rules.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "rule_allowed_values")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@ToString(exclude = "rule")
@EqualsAndHashCode(exclude = "rule")
public class RuleAllowedValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "value_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    @JsonIgnore
    private ValidationRule rule;

    @Column(name = "allowed_value", nullable = false, length = 100)
    private String allowedValue;

    @Column(name = "value_label", length = 255)
    private String valueLabel;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false)
    @Builder.Default
    private Long createdBy = 0L;

    @Column(name = "created_by_name", nullable = false, length = 100)
    @Builder.Default
    private String createdByName = "system";

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (createdBy == null)     createdBy = 0L;
        if (createdByName == null) createdByName = "system";
    }
}