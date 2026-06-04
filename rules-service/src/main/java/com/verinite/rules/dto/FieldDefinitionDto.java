package com.verinite.rules.dto;

import com.verinite.common.enums.DataType;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldDefinitionDto {

    private Long    id;
    private Long    profileId;
    private String  profileName;
    private String  mti;
    private String  deNumber;
    private String  fieldName;
    private DataType dataType;
    private Integer maxLength;
    private Boolean isLlvar;
    private Boolean isLllvar;
    private Boolean isMandatory;
    private String  placeholderValue;
    private Integer displayOrder;
    private Boolean isBuilderVisible;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String  createdByName;
    private String  updatedByName;
    private String description;
}