package com.verinite.auth_service.entity;

import com.verinite.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(unique = true)
    private String jti;          // JWT ID — unique per token

    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String revokeReason; // LOGOUT, PASSWORD_CHANGE, ADMIN
    private String ipAddress;
    private String userAgent;
}