package com.verinite.profile.dto;

import com.verinite.profile.entity.MessageFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FormatDto {

    private Long                   id;
    private Long                   profileId;
    private String                 formatName;
    private String                 isoVersion;
    private MessageFormat.Encoding encoding;
    private String                 mti;
    private Integer                totalFields;
    private MessageFormat.Status   status;
    private String                 xmlContent;
    private String                 checksum;
    private Integer                currentVersion;
    private String                 description;
    private String                 createdBy;
    private String                 updatedBy;
    private LocalDateTime          createdAt;
    private LocalDateTime          updatedAt;
}