package com.verinite.auth_service.entity;

import com.verinite.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    private String fullName;

    private String avatarInitials;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime lockedUntil;

    @Builder.Default
    private int failedLoginCount = 0;

    private LocalDateTime passwordChangedAt;

    private LocalDateTime deletedAt;
}