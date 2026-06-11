package com.verinite.profile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "message_format_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_format_versions",
                columnNames = {"format_id", "version_number"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageFormatVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ── FK (writable raw column) ──────────────────────────────────────
    @Column(name = "format_id", nullable = false)
    private Long formatId;

    // ── JPA navigation — read-only, driven by formatId ───────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "format_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MessageFormat format;

    // ── Version identity ──────────────────────────────────────────────
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "xml_content", nullable = false, columnDefinition = "LONGTEXT")
    private String xmlContent;

    // checksum is NOT NULL in the DB schema
    @Column(name = "checksum", nullable = false, length = 64)
    private String checksum;

    @Column(name = "change_note", length = 500)
    private String changeNote;

    // ── Version state ─────────────────────────────────────────────────
    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = false;

    @Column(name = "validated_ok", nullable = false)
    @Builder.Default
    private Boolean validatedOk = false;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    // ── Audit ─────────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;
}