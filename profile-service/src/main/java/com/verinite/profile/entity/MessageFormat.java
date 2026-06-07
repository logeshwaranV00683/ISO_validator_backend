package com.verinite.profile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_formats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageFormat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Column(name = "format_name", nullable = false)
    private String formatName;

    @Column(name = "iso_version")
    private String isoVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "encoding", nullable = false)
    @Builder.Default
    private Encoding encoding = Encoding.ASCII;

    @Column(name = "mti", length = 4)
    private String mti;

    @Column(name = "xml_content", nullable = false, columnDefinition = "LONGTEXT")
    private String xmlContent;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "current_version", nullable = false)
    @Builder.Default
    private Integer currentVersion = 1;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Enumerated(EnumType.STRING)
    private Status status;

    public enum Encoding {
        ASCII, EBCDIC, Binary
    }

    public enum Status{
        active, inactive
    }
}