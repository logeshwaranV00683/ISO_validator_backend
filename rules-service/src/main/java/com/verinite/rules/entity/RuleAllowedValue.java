package com.verinite.rules.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rule_allowed_values")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleAllowedValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    private ValidationRule rule;

    @Column(name = "value", nullable = false, length = 255)
    private String value;
}