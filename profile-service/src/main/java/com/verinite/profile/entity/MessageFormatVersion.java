package com.verinite.profile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "message_format_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageFormatVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "format_id", nullable = false)
    private Long formatId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "xml_content", nullable = false, columnDefinition = "TEXT")
    private String xmlContent;

    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}