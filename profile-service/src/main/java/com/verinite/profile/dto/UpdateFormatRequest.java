package com.verinite.profile.dto;

import com.verinite.profile.entity.MessageFormat;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * All fields are optional.
 * - Sending xmlContent triggers a new version snapshot + cache invalidation.
 * - Sending only metadata fields (formatName, mti, etc.) updates without
 *   bumping the version or touching the XML.
 */
@Data
public class UpdateFormatRequest {

    private String xmlContent;                 // nullable — omit to update metadata only

    @Size(max = 500, message = "Change note must not exceed 500 characters")
    private String changeNote;

    @Size(max = 100, message = "Format name must not exceed 100 characters")
    private String formatName;

    @Size(max = 60, message = "ISO version must not exceed 60 characters")
    private String isoVersion;

    private MessageFormat.Encoding encoding;

    @Size(max = 4, message = "MTI must not exceed 4 characters")
    private String mti;

    private Integer totalFields;

    private String description;
}