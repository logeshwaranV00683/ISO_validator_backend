package com.verinite.rules.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "field_definitions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "mti", nullable = false, length = 4)
    private String mti;

    @Column(name = "field_number", nullable = false)
    private Integer fieldNumber;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "data_type", nullable = false)
    private String dataType;

    @Column(name = "is_builder_visible", nullable = false)
    private Boolean isBuilderVisible;
}