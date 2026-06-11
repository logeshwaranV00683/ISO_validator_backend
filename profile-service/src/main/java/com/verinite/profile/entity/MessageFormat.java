package com.verinite.profile.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "message_formats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_formats_profile_name",
                columnNames = {"profile_id", "format_name"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageFormat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ── FK (writable raw column) ──────────────────────────────────────
    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    // ── JPA navigation — read-only, driven by profileId ──────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private SwitchProfile profile;

    // ── Identity ──────────────────────────────────────────────────────
    @Column(name = "format_name", nullable = false, length = 100)
    private String formatName;

    @Column(name = "iso_version", length = 60)
    private String isoVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "encoding", nullable = false,
            columnDefinition = "ENUM('ASCII','EBCDIC','Binary')")
    @Builder.Default
    private Encoding encoding = Encoding.ASCII;

    @Column(name = "mti", length = 4)
    private String mti;

    @Column(name = "total_fields", nullable = false)
    @Builder.Default
    private Integer totalFields = 128;

    // ── Status ────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "ENUM('active','inactive')")
    @Builder.Default
    private Status status = Status.active;

    // ── Content ───────────────────────────────────────────────────────
    @Column(name = "xml_content", nullable = false, columnDefinition = "LONGTEXT")
    private String xmlContent;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "current_version", nullable = false)
    @Builder.Default
    private Integer currentVersion = 1;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ── Soft-delete ───────────────────────────────────────────────────
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ── Audit ─────────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    // ── Enums ─────────────────────────────────────────────────────────
    public enum Encoding {
        ASCII, EBCDIC, Binary
    }

    public enum Status {
        active, inactive
    }
}