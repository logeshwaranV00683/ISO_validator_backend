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

    @Column(name = "mti", nullable = false, length = 4)
    private String mti;

    @Column(name = "xml_content", nullable = false, columnDefinition = "TEXT")
    private String xmlContent;

    @Column(name = "current_version", nullable = false)
    @Builder.Default
    private Integer currentVersion = 1;

    @Column(name = "deleted_at", nullable = false)
    @Builder.Default
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}